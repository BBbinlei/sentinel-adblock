package com.sentinel.vpn.decide

import com.sentinel.rules.domain.*
import com.sentinel.rules.model.RuleLevel
import com.sentinel.data.db.*
import com.sentinel.data.contract.RewardWindowContract

data class DnsContext(val cfg: EffectiveConfig?, val disabledRules: Set<String>, val rewardWindowOpen: Boolean)
enum class DnsVerdict { FORWARD, BLOCK, WOULD_BLOCK }
data class DnsDecision(val verdict: DnsVerdict, val hit: DomainHit?)
object DnsDecider {
    fun decide(domain: String, matcher: DomainMatcher?, ctx: DnsContext): DnsDecision {
        val hit = matcher?.lookup(domain) ?: return DnsDecision(DnsVerdict.FORWARD, null)
        val verdict = when {
            ctx.cfg?.level == ProtectLevel.OFF -> DnsVerdict.FORWARD
            hit.level == RuleLevel.STRONG && ctx.cfg?.level != ProtectLevel.STRONG -> DnsVerdict.FORWARD
            hit.ruleId in ctx.disabledRules -> DnsVerdict.FORWARD
            hit.tag in RewardWindowContract.RELEASED_TAGS && ctx.rewardWindowOpen -> DnsVerdict.FORWARD
            ctx.cfg?.observing == true -> DnsVerdict.WOULD_BLOCK
            else -> DnsVerdict.BLOCK
        }
        return DnsDecision(verdict, hit)
    }
}
