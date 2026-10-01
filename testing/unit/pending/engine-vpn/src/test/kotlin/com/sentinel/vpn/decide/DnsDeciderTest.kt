package com.sentinel.vpn.decide

import com.sentinel.data.db.ProtectLevel
import com.sentinel.rules.model.*
import com.sentinel.vpn.fakes.*
import kotlin.test.*
import org.junit.Test

class DnsDeciderTest {
    private val ad = rule(); private val sdk = rule("sdk.example.test", DomainTag.AD_SDK)
    private val strong = rule("strong.example.test", level = RuleLevel.STRONG)
    private val rules = matcher(ad, sdk, strong)
    private fun ctx(level: ProtectLevel = ProtectLevel.STANDARD, observing: Boolean = false,
                    disabled: Set<String> = emptySet(), reward: Boolean = false) = DnsContext(config(level = level, observing = observing), disabled, reward)
    @Test fun `UT-VP-2-01 unavailable matcher and misses forward`() {
        assertEquals(DnsVerdict.FORWARD, DnsDecider.decide(ad.domain, null, ctx()).verdict)
        val missed = DnsDecider.decide("normal.example.test", rules, ctx())
        assertEquals(DnsVerdict.FORWARD, missed.verdict); assertNull(missed.hit)
    }
    @Test fun `UT-VP-2-02 OFF wins over observing and reward`() {
        assertEquals(DnsVerdict.FORWARD, DnsDecider.decide(ad.domain, rules, ctx(ProtectLevel.OFF, observing = true)).verdict)
    }
    @Test fun `UT-VP-2-03 strong requires strong App including null default`() {
        assertEquals(DnsVerdict.FORWARD, DnsDecider.decide(strong.domain, rules, ctx()).verdict)
        assertEquals(DnsVerdict.FORWARD, DnsDecider.decide(strong.domain, rules, DnsContext(null, emptySet(), false)).verdict)
        assertEquals(DnsVerdict.BLOCK, DnsDecider.decide(strong.domain, rules, ctx(ProtectLevel.STRONG)).verdict)
    }
    @Test fun `UT-VP-2-04 disabled rule wins over observing`() {
        assertEquals(DnsVerdict.FORWARD, DnsDecider.decide(ad.domain, rules, ctx(observing = true, disabled = setOf(ad.id))).verdict)
        assertEquals(DnsVerdict.BLOCK, DnsDecider.decide(ad.domain, rules, ctx(disabled = setOf(sdk.id))).verdict)
    }
    @Test fun `UT-VP-2-05 reward releases SDK only`() {
        assertEquals(DnsVerdict.FORWARD, DnsDecider.decide(sdk.domain, rules, ctx(reward = true, observing = true)).verdict)
        assertEquals(DnsVerdict.BLOCK, DnsDecider.decide(sdk.domain, rules, ctx()).verdict)
        assertEquals(DnsVerdict.BLOCK, DnsDecider.decide(ad.domain, rules, ctx(reward = true)).verdict)
    }
    @Test fun `UT-VP-2-06 observing records would block`() {
        val result = DnsDecider.decide(ad.domain, rules, ctx(observing = true))
        assertEquals(DnsVerdict.WOULD_BLOCK, result.verdict); assertEquals(ad.id, assertNotNull(result.hit).ruleId)
    }
    @Test fun `UT-VP-2-07 matching ordinary rule blocks`() {
        val result = DnsDecider.decide("sub.${ad.domain}", rules, ctx())
        assertEquals(DnsVerdict.BLOCK, result.verdict); assertEquals(ad.id, assertNotNull(result.hit).ruleId)
        assertEquals(DomainTag.AD, result.hit?.tag)
        assertEquals(DnsVerdict.BLOCK, DnsDecider.decide(ad.domain, rules, DnsContext(null, emptySet(), false)).verdict)
    }
}
