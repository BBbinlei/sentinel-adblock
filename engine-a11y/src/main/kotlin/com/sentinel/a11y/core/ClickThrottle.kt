package com.sentinel.a11y.core

/** 同一规则每次启动最多点击 3 次，两次间隔 >= 1000ms。 */
class ClickThrottle(private val clock: () -> Long) {
    private class Record(var count: Int, var lastAt: Long)
    private val records = HashMap<Pair<String, String>, Record>()

    /** 允许则同时登记这次点击。 */
    fun allow(launchKey: String, ruleId: String): Boolean {
        val now = clock()
        val key = launchKey to ruleId
        val r = records[key]
        if (r == null) {
            records[key] = Record(1, now)
            return true
        }
        if (r.count >= MAX_CLICKS || now - r.lastAt < MIN_INTERVAL_MS) return false
        r.count++
        r.lastAt = now
        return true
    }

    fun reset(launchKey: String) {
        records.keys.removeAll { it.first == launchKey }
    }

    companion object {
        const val MAX_CLICKS = 3
        const val MIN_INTERVAL_MS = 1_000L
    }
}
