package com.sentinel.vpn.dns

class DnsCache(private val capacity: Int = 2000, private val clock: () -> Long) {
    init { require(capacity > 0) }
    private data class Entry(val response: ByteArray, val until: Long)
    private val entries = LinkedHashMap<Pair<String, Int>, Entry>(capacity, 0.75f, true)
    @Synchronized fun get(name: String, qtype: Int): ByteArray? {
        val key = name.lowercase(java.util.Locale.ROOT) to qtype
        val entry = entries[key] ?: return null
        if (entry.until <= clock()) { entries.remove(key); return null }
        return entry.response.copyOf()
    }
    @Synchronized fun put(name: String, qtype: Int, resp: ByteArray) {
        val ttl = DnsMessage.minTtl(resp)?.coerceIn(30, 3600) ?: return
        entries[name.lowercase(java.util.Locale.ROOT) to qtype] = Entry(resp.copyOf(), clock() + ttl * 1000L)
        if (entries.size > capacity) entries.remove(entries.keys.first())
    }
}
