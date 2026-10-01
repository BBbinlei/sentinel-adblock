package com.sentinel.rules.parse

import com.sentinel.rules.model.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class HostsParserTest {
    @Test fun `UT-CR-1-01 hosts comments and normalization`() {
        val result = HostsParser.parse("# c\n0.0.0.0 Ad.Example.com.\n127.0.0.1 localhost\n127.0.0.1 t.x.cn # tail\n", "test", RuleLevel.STANDARD, DomainTag.AD)
        assertEquals(listOf("ad.example.com", "t.x.cn"), result.rules.map { it.domain })
        assertEquals("dns:ad.example.com", result.rules.first().id)
    }
    @Test fun `UT-CR-1-02 plain domain`() {
        assertEquals(listOf("a.cn"), HostsParser.parse("a.cn\n", "test", RuleLevel.STANDARD, DomainTag.AD).rules.map { it.domain })
    }
    @Test fun `UT-CR-1-04 reject IP wildcard and invalid domains`() {
        for (raw in listOf("1.2.3.4", "*.x.com", "::1", "localhost", "a..com", "-a.com", "a_.com", "a/b.com"))
            assertNull(DomainNormalizer.normalize(raw), raw)
        assertEquals("a.cn", DomainNormalizer.normalize(" A.CN. "))
    }
}
