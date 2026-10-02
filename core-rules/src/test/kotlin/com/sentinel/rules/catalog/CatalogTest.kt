package com.sentinel.rules.catalog

import com.sentinel.rules.model.*
import java.net.InetAddress
import kotlin.test.*
import org.junit.jupiter.api.Test

class CatalogTest {
    @Test fun UT_CR_3_01_sensitive_apps() {
        assertTrue(SensitiveApps.isSensitive("com.new.bank", "某某银行"))
        assertTrue(SensitiveApps.isSensitive("com.tencent.mm", null))
        assertFalse(SensitiveApps.isSensitive("com.android.settings", "设置"))
    }
    @Test fun UT_CR_3_02_httpdns_cidrs() {
        assertTrue(HttpDnsCatalog.cidrs.any { it.contains(InetAddress.getByName("203.107.1.33").address) })
        assertEquals(58, HttpDnsCatalog.domains.size)
        assertEquals(61, HttpDnsCatalog.cidrs.size)
        assertTrue(HttpDnsCatalog.rules().all { it.tag == DomainTag.HTTPDNS && it.level == RuleLevel.STANDARD })
        assertTrue(Cidr.parse("240e:928:1400:10::25/128").contains(InetAddress.getByName("240e:928:1400:10::25").address))
        assertFalse(Cidr.parse("203.107.1.0/24").contains(InetAddress.getByName("203.107.2.33").address))
        assertFalse(Cidr.parse("203.107.1.0/24").contains(ByteArray(16)))
        assertFailsWith<IllegalArgumentException> { Cidr.parse("203.107.1.0/33") }
    }
    @Test fun UT_CR_3_03_landing_catalog() {
        assertTrue(JumpTargetCatalog.isAdLanding("com.xunmeng.pinduoduo"))
        assertFalse(JumpTargetCatalog.isAdLanding("com.android.settings"))
        assertEquals(10, StandardDomains.rules().size)
        assertTrue(StandardDomains.rules().all { it.level == RuleLevel.STANDARD })
    }
}
