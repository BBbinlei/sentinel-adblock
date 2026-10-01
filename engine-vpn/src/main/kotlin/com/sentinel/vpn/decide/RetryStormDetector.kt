package com.sentinel.vpn.decide

class RetryStormDetector(private val clock: () -> Long) {
    private data class Window(val times: ArrayDeque<Long> = ArrayDeque(), var last: Long? = null)
    private val windows = mutableMapOf<Pair<String, String>, Window>()
    @Synchronized fun record(pkg: String, ruleId: String): Boolean {
        val now = clock()
        windows.entries.removeAll { (_, v) -> now - (v.times.lastOrNull() ?: 0) > 600_000 && (v.last == null || now - v.last!! >= 600_000) }
        val w = windows.getOrPut(pkg to ruleId) { Window() }
        while (w.times.isNotEmpty() && now - w.times.first() > 60_000) w.times.removeFirst()
        w.times.addLast(now)
        // Only the ten newest samples are needed to establish the threshold.
        if (w.times.size > 10) w.times.removeFirst()
        if (w.times.size < 10 || w.last?.let { now - it < 600_000 } == true) return false
        w.last = now
        return true
    }
}
