package com.sentinel.rules.domain

import com.sentinel.rules.model.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.*
import org.junit.jupiter.api.Test

class DomainMatcherTest {
    private fun rule(domain: String, tag: DomainTag = DomainTag.AD, level: RuleLevel = RuleLevel.STRONG) =
        DnsRule("dns:$domain", domain, tag, level, "test")
    private val bytes get() = DomainCompiler.compile(listOf(rule("x.com"), rule("ads.x.com", DomainTag.AD_SDK, RuleLevel.STANDARD)))
    @Test fun `UT-CR-2-01 most specific suffix`() {
        val matcher = DomainMatcher(ByteBuffer.wrap(bytes))
        assertEquals("ads.x.com", matcher.lookup("a.ads.x.com")?.ruleDomain)
        assertEquals(DomainTag.AD_SDK, matcher.lookup("ADS.X.COM.")?.tag)
        assertEquals("x.com", matcher.lookup("cdn.x.com")?.ruleDomain)
        assertEquals("dns:x.com", matcher.lookup("cdn.x.com")?.ruleId)
        for (name in listOf("x.cn", "notx.com")) assertNull(matcher.lookup(name))
        assertEquals(2, matcher.size)
    }
    @Test fun `UT-CR-2-02 never block wins`() {
        val matcher = DomainMatcher(ByteBuffer.wrap(DomainCompiler.compile(listOf(rule("alipay.com")))))
        assertNull(matcher.lookup("m.alipay.com"))
        assertFalse(NeverBlockList.contains("notalipay.com"))
    }
    @Test fun `UT-CR-2-03 validation and memory mapped load`() {
        val data = bytes
        assertTrue(DomainMatcher.verify(data))
        assertFalse(DomainMatcher.verify(data.copyOf(data.size - 1)))
        assertFalse(DomainMatcher.verify(data.copyOf().also { it[0] = 0 }))
        assertFalse(DomainMatcher.verify(data.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(10, Int.MAX_VALUE) }))
        val path = Files.createTempFile("sentinel-domains", ".bin")
        try {
            Files.write(path, data)
            assertEquals("x.com", DomainMatcher.load(path.toFile()).lookup("x.com")?.ruleDomain)
        } finally { Files.delete(path) }
        assertEquals(0, DomainMatcher(ByteBuffer.wrap(DomainCompiler.compile(emptyList()))).size)
    }
    @Test fun `UT-CR-2-04 duplicate keeps standard`() {
        val matcher = DomainMatcher(ByteBuffer.wrap(DomainCompiler.compile(listOf(rule("x.com"), rule("x.com", level = RuleLevel.STANDARD)))))
        assertEquals(RuleLevel.STANDARD, matcher.lookup("x.com")?.level)
        assertEquals(1, matcher.size)
    }
}
