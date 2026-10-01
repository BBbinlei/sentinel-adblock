package com.sentinel.vpn.dns

import com.sentinel.vpn.fakes.Wire
import kotlin.test.*
import org.junit.Test

class DnsCacheTest {
    @Test fun `UT-VP-3-05 cache TTL is minimum bounded to 30 and 3600 seconds`() {
        for ((ttl, expiresIn) in listOf(1 to 30, 30 to 30, 90 to 90, 3600 to 3600, 7200 to 3600)) {
            var now = 100_000L; val cache = DnsCache(clock = { now }); val q = Wire.query()
            val response = Wire.answer(q, ttl = ttl)
            assertEquals(ttl, DnsMessage.minTtl(response)); cache.put("ads.example.test", 1, response)
            now += expiresIn * 1000L - 1; assertContentEquals(response, assertNotNull(cache.get("ads.example.test", 1)))
            assertNull(cache.get("ads.example.test", 28)); now++; assertNull(cache.get("ads.example.test", 1))
        }
        var now = 0L; val cache = DnsCache(clock = { now }); val q = Wire.query()
        val two = Wire.answer(q, ttl = 120).also { Wire.put16(it, 6, 2) } + Wire.answer(q, ttl = 45).copyOfRange(q.size, Wire.answer(q).size)
        assertEquals(45, DnsMessage.minTtl(two)); cache.put("ads.example.test", 1, two)
        now = 44_999; assertNotNull(cache.get("ads.example.test", 1)); now++; assertNull(cache.get("ads.example.test", 1))
        val bounded = DnsCache(capacity = 2, clock = { 0L })
        bounded.put("a.test", 1, Wire.answer(q)); bounded.put("b.test", 1, Wire.answer(q)); bounded.put("c.test", 1, Wire.answer(q))
        assertEquals(2, listOf("a.test", "b.test", "c.test").count { bounded.get(it, 1) != null })
    }
}
