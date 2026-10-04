package com.sentinel.app.launch

import android.content.*
import android.content.pm.PackageManager
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import rikka.shizuku.Shizuku

/** Process singleton retains its Shizuku connection when the detail page is closed. */
class OriginalIconControl private constructor(private val context: Context) {
    val state = MutableStateFlow("正在检查")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences("launch", Context.MODE_PRIVATE)
    private var remote: IBinder? = null
    private var binding = false
    private val args = originalEntryServiceArgs(context)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binding = false; remote = service
            scope.launch { transact(if (requested()) 1 else 2, requested()) }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            binding = false; remote = null
            state.value = if (requested()) "转接服务已断开，原图标将普通启动" else "已关闭原图标自动转接"
        }
    }
    init {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            remote = null; binding = false; state.value = "Shizuku 已停止，原图标将普通启动"
        }
        Shizuku.addRequestPermissionResultListener { code, _ -> if (code == 7104) refresh() }
        refresh()
    }
    private fun requested() = prefs.getBoolean("originalIconEnabled", false)
    fun refresh() {
        when {
            Build.VERSION.SDK_INT != 31 -> state.value = "当前系统未验证（仅支持 Android 12 / API 31）"
            LaunchShortcuts(context).status(LaunchCatalog.policy.byId("yangshipin")!!) != LaunchStatus.VERIFIED ->
                state.value = "央视频版本或入口不支持，原图标将普通启动"
            !Shizuku.pingBinder() -> state.value = "Shizuku 未运行"
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> state.value = "需要 Shizuku 授权"
            remote?.isBinderAlive == true -> scope.launch { transact(2, false) }
            requested() -> connect()
            else -> state.value = "已关闭原图标自动转接"
        }
    }
    fun enable() {
        if (Build.VERSION.SDK_INT != 31 || LaunchShortcuts(context).status(LaunchCatalog.policy.byId("yangshipin")!!) != LaunchStatus.VERIFIED) { refresh(); return }
        if (!Shizuku.pingBinder()) { refresh(); return }
        prefs.edit().putBoolean("originalIconEnabled", true).apply()
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(7104); state.value = "等待 Shizuku 授权"; return
        }
        if (remote?.isBinderAlive == true) scope.launch { transact(1, true) } else connect()
    }
    fun disable() {
        prefs.edit().putBoolean("originalIconEnabled", false).apply()
        scope.launch {
            if (remote?.isBinderAlive == true) transact(1, false)
            runCatching { Shizuku.unbindUserService(args, connection, true) }
            remote = null; binding = false; refresh()
        }
    }
    private fun connect() {
        if (binding) return
        binding = true; state.value = "正在连接转接服务"
        runCatching { Shizuku.bindUserService(args, connection) }.onFailure {
            binding = false; state.value = "连接失败：${it.javaClass.simpleName}"
        }
        scope.launch {
            delay(10000)
            if (binding) { binding = false; state.value = "转接服务连接超时，请重新启用" }
        }
    }
    private suspend fun transact(code: Int, enabled: Boolean) {
        val binder = remote ?: return
        state.value = withContext(Dispatchers.IO) {
            val data = Parcel.obtain(); val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(OriginalEntryUserService.TOKEN)
                if (code == 1) data.writeInt(if (enabled) 1 else 0)
                check(binder.transact(code, data, reply, 0))
                reply.readException(); reply.readString() ?: "状态未知"
            } catch (e: Exception) { "转接服务不可用：${e.javaClass.simpleName}；原图标将普通启动" }
            finally { data.recycle(); reply.recycle() }
        }
    }
    companion object {
        @Volatile private var instance: OriginalIconControl? = null
        fun get(context: Context): OriginalIconControl = instance ?: synchronized(this) {
            instance ?: OriginalIconControl(context.applicationContext).also { instance = it }
        }
    }
}

internal fun originalEntryServiceArgs(context: Context) =
    Shizuku.UserServiceArgs(ComponentName(context, OriginalEntryUserService::class.java))
        .tag("sentinel-original-entry").processNameSuffix("original-entry").version(4).daemon(true)
