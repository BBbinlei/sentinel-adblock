package com.sentinel.a11y.core

import com.sentinel.data.db.SignalKind

data class HealthSignal(val pkg: String, val kind: SignalKind, val detail: String?)

class HealthSignals(private val clock: () -> Long, private val labelToPkg: (String) -> String?) {
    private val lastEmitted = HashMap<Pair<String, SignalKind>, Long>()

    /** 崩溃弹窗：系统窗口里出现「XX 已停止运行」。 */
    fun onWindowText(windowPkg: String, texts: List<String>): HealthSignal? {
        if (windowPkg != SYSTEM_PKG) return null
        for (text in texts) {
            val label = CRASH.find(text)?.groupValues?.get(1)?.trim() ?: continue
            val pkg = labelToPkg(label) ?: continue
            return emitOnce(pkg, SignalKind.CRASH_DIALOG, label) ?: return null
        }
        return null
    }

    /** 冷启动循环：60 秒内 >= 3 次。 */
    fun onLaunch(pkg: String, launchesIn60s: Int): HealthSignal? =
        if (launchesIn60s >= LOOP_THRESHOLD) emitOnce(pkg, SignalKind.COLD_START_LOOP, launchesIn60s.toString()) else null

    private fun emitOnce(pkg: String, kind: SignalKind, detail: String?): HealthSignal? {
        val now = clock()
        val key = pkg to kind
        val last = lastEmitted[key]
        if (last != null && now - last < DEDUP_MS) return null
        lastEmitted[key] = now
        return HealthSignal(pkg, kind, detail)
    }

    companion object {
        const val SYSTEM_PKG = "android"
        const val LOOP_THRESHOLD = 3
        const val DEDUP_MS = 600_000L
        private val CRASH = Regex("(.+?)(已停止运行|屡次停止运行|没有响应|已停止)")
    }
}
