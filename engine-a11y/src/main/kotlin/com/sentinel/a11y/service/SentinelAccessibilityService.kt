package com.sentinel.a11y.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.sentinel.a11y.core.*
import com.sentinel.a11y.di.A11yRuntime
import com.sentinel.data.db.EngineId
import com.sentinel.data.db.EngineState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import org.koin.core.context.GlobalContext

class SentinelAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val runtime get() = GlobalContext.get().get<A11yRuntime>()

    private var brain: A11yBrain? = null
    private var keeper: VolumeKeeper? = null
    private var executor: ActionExecutor? = null
    private var overlay: OverlayToast? = null
    private var contentJob: Job? = null
    private var tickJob: Job? = null
    private var labelJob: Job? = null

    private var lastStatePkg: String? = null
    private val lastActivity = HashMap<String, String?>()
    private val activityChecks = HashMap<String, Boolean>()
    @Volatile private var lastAutoClickAt = 0L

    private var launcherCache: Set<String> = emptySet()
    private var launcherCacheAt = 0L

    private fun launchers(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - launcherCacheAt > 60_000) {
            launcherCache = try {
                packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                    .map { it.activityInfo.packageName }.toSet()
            } catch (e: Exception) { Log.w(A11yRuntime.TAG, "查询桌面失败", e); launcherCache }
            launcherCacheAt = now
        }
        return launcherCache
    }

    private fun currentIme(): String? = try {
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)?.packageName
    } catch (_: Exception) { null }

    private fun ignored(): Set<String> =
        launchers() + setOfNotNull(packageName, "com.android.systemui", currentIme())

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            current = this
            val audio = getSystemService(AudioManager::class.java)
            val prefs = getSharedPreferences("sentinel-a11y", Context.MODE_PRIVATE)
            val volumeKeeper = VolumeKeeper(object : VolumePort {
                override fun get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                override fun set(v: Int) = audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
            }, object : KeyValueStore {
                override fun getInt(k: String): Int? = if (prefs.contains(k)) prefs.getInt(k, 0) else null
                override fun putInt(k: String, v: Int) { prefs.edit().putInt(k, v).commit() }
                override fun remove(k: String) { prefs.edit().remove(k).commit() }
            })
            keeper = volumeKeeper
            volumeKeeper.restoreIfPending()

            val rt = runtime
            val b = A11yBrain(rt.state, RewardedHandler(volumeKeeper) { System.currentTimeMillis() },
                { System.currentTimeMillis() }, ::ignored, ::launchers, { rt.labels[it] },
                onError = { Log.w(A11yRuntime.TAG, "A11yBrain 异常", it) })
            brain = b
            val ov = OverlayToast(this)
            overlay = ov
            executor = ActionExecutor(this, rt, ov, scope, b::onRewardedChoice) {
                lastAutoClickAt = System.currentTimeMillis()
            }
            scope.launch {
                try {
                    rt.reloadIndex()
                    rt.statuses.report(EngineId.A11Y, EngineState.RUNNING)
                } catch (e: Exception) { warn("服务启动上报失败", e) }
            }
            labelJob = scope.launch {
                try {
                    rt.appConfigs.observeAll().map { rows -> rows.associate { it.label to it.pkg } }
                        .collect { rt.labels = it }
                } catch (e: Exception) { currentCoroutineContext().ensureActive(); warn("应用名订阅失败", e) }
            }
            tickJob = scope.launch {
                while (isActive) {
                    delay(1_000)
                    try {
                        if (b.rewardedActive) executor?.execute(b.onTick())
                    } catch (e: Exception) { currentCoroutineContext().ensureActive(); warn("tick 失败", e) }
                }
            }
        } catch (e: Exception) { warn("onServiceConnected 失败", e) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            event ?: return
            val pkg = event.packageName?.toString() ?: return
            val ts = System.currentTimeMillis()
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    contentJob?.cancel()
                    val activity = activityOf(pkg, event.className?.toString())
                    lastStatePkg = pkg
                    lastActivity[pkg] = activity
                    dispatchWindow(UiInput.WindowChanged(pkg, activity, ts), event.source)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    if (pkg != lastStatePkg) return
                    contentJob?.cancel()
                    contentJob = scope.launch {
                        delay(DEBOUNCE_MS)
                        process(UiInput.WindowChanged(pkg, lastActivity[pkg], System.currentTimeMillis()), null)
                    }
                }
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    // 服务自己发出的点击不是用户交互。
                    if (ts - lastAutoClickAt < SELF_CLICK_MS) return
                    val src = event.source
                    val text = src?.text?.toString() ?: event.text.joinToString("").ifEmpty { null }
                    val input = UiInput.Clicked(pkg, text, src?.contentDescription?.toString() ?: event.contentDescription?.toString(),
                        src?.viewIdResourceName, src?.className?.toString() ?: event.className?.toString(), ts)
                    scope.launch { process(input, null) }
                }
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED, AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    if (ts - lastAutoClickAt < SELF_CLICK_MS) return
                    scope.launch { process(UiInput.Interaction(pkg, ts), null) }
                }
            }
        } catch (e: Exception) { warn("事件处理失败", e) }
    }

    private fun dispatchWindow(input: UiInput.WindowChanged, source: AccessibilityNodeInfo?) {
        scope.launch { process(input, source) }
    }

    private suspend fun process(input: UiInput, source: AccessibilityNodeInfo?) {
        try {
            val b = brain ?: return
            val rt = runtime
            val pkg = when (input) {
                is UiInput.WindowChanged -> input.pkg
                is UiInput.Interaction -> input.pkg
                is UiInput.Clicked -> input.pkg
            }
            val prev = prevPkg
            val relevant = if (input is UiInput.WindowChanged) listOfNotNull(pkg, prev) else listOf(pkg)
            rt.prefetch(relevant, if (input is UiInput.WindowChanged && prev != null && prev != pkg) prev to pkg else null)
            val root = if (input is UiInput.WindowChanged && b.wantsTree(pkg, input.activity)) {
                val r = source ?: rootInActiveWindow
                r?.takeIf { it.packageName?.toString() == pkg }?.let(::NodeViewAdapter)
            } else null
            val actions = b.onInput(input, root)
            if (input is UiInput.WindowChanged && pkg !in ignored()) prevPkg = pkg
            executor?.execute(actions)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            warn("输入处理失败", e)
        }
    }

    private var prevPkg: String? = null

    private fun activityOf(pkg: String, className: String?): String? {
        if (className == null) return null
        val key = "$pkg/$className"
        return if (activityChecks.getOrPut(key) {
            try { packageManager.getActivityInfo(ComponentName(pkg, className), 0); true }
            catch (_: Exception) { false }
        }) className else null
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private var stopped = false
    private fun shutdown() {
        if (stopped) return
        stopped = true
        if (current === this) current = null
        try { keeper?.restore() } catch (e: Exception) { warn("恢复音量失败", e) }
        overlay?.dismiss()
        contentJob?.cancel(); tickJob?.cancel(); labelJob?.cancel()
        val rt = try { runtime } catch (_: Exception) { null }
        // 状态上报在独立协程里完成，不随服务作用域取消。
        if (rt != null) CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { rt.statuses.report(EngineId.A11Y, EngineState.STOPPED) } catch (e: Exception) { warn("停止上报失败", e) }
        }
        scope.cancel()
    }

    private fun warn(msg: String, e: Throwable) { Log.w(A11yRuntime.TAG, msg, e) }

    companion object {
        /** 当前已连接的服务实例（调试版快照录制使用）。 */
        @Volatile var current: SentinelAccessibilityService? = null
            private set
        private const val DEBOUNCE_MS = 100L
        private const val SELF_CLICK_MS = 600L
    }
}
