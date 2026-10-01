package com.sentinel.a11y.core

sealed interface UiInput {
    /** activity 为 null 表示弹窗/非 Activity 窗口。 */
    data class WindowChanged(val pkg: String, val activity: String?, val ts: Long) : UiInput
    /** 点击/长按/滚动。 */
    data class Interaction(val pkg: String, val ts: Long) : UiInput
    data class Clicked(val pkg: String, val text: String?, val desc: String?, val viewId: String?,
                       val className: String?, val ts: Long) : UiInput
}

data class LaunchInfo(val pkg: String, val startedAt: Long, val fromLauncher: Boolean)
data class Transition(val from: String, val to: String, val ts: Long)

/**
 * 跟踪前台 App、启动信息与交互时间。忽略包（自身、systemui、输入法、桌面）不算前台变化，
 * 但桌面窗口会被记为「下一次启动的来源」。
 */
class ForegroundTracker(private val ignored: () -> Set<String>, private val launchers: () -> Set<String>) {
    var currentPkg: String? = null
        private set
    var currentActivity: String? = null
        private set
    var launch: LaunchInfo? = null
        private set

    private var originIsLauncher = false
    private val interactions = HashMap<String, Long>()
    private val windows = HashMap<String, Long>()
    private val launches = HashMap<String, ArrayList<Long>>()

    fun onInput(i: UiInput): Transition? = when (i) {
        is UiInput.Interaction -> { interactions[i.pkg] = i.ts; null }
        is UiInput.Clicked -> { interactions[i.pkg] = i.ts; null }
        is UiInput.WindowChanged -> onWindow(i)
    }

    private fun onWindow(w: UiInput.WindowChanged): Transition? {
        windows[w.pkg] = w.ts
        if (w.pkg in ignored()) {
            originIsLauncher = w.pkg in launchers()
            return null
        }
        if (w.pkg == currentPkg) {
            if (w.activity != null) currentActivity = w.activity
            return null
        }
        if (w.activity == null) return null // 其他包的非 Activity 窗口不改变前台。
        val from = currentPkg
        currentPkg = w.pkg
        currentActivity = w.activity
        launch = LaunchInfo(w.pkg, w.ts, originIsLauncher)
        originIsLauncher = false
        launches.getOrPut(w.pkg) { ArrayList() }.also { list ->
            list += w.ts
            list.removeAll { it < w.ts - RETAIN_MS }
        }
        return from?.let { Transition(it, w.pkg, w.ts) }
    }

    fun lastInteractionAt(pkg: String): Long? = interactions[pkg]
    fun lastWindowAt(pkg: String): Long? = windows[pkg]
    fun launchesWithin(pkg: String, windowMs: Long, now: Long): Int =
        launches[pkg].orEmpty().count { it > now - windowMs && it <= now }

    /** 复制当前状态，供在前台变化前后分别判定（跳转回退需要源 App 的启动信息）。 */
    fun copy(): ForegroundTracker {
        val c = ForegroundTracker(ignored, launchers)
        c.currentPkg = currentPkg
        c.currentActivity = currentActivity
        c.launch = launch
        c.originIsLauncher = originIsLauncher
        c.interactions.putAll(interactions)
        c.windows.putAll(windows)
        launches.forEach { (k, v) -> c.launches[k] = ArrayList(v) }
        return c
    }

    private companion object { const val RETAIN_MS = 600_000L }
}
