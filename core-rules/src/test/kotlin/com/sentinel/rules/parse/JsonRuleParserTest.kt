package com.sentinel.rules.parse

import com.sentinel.rules.model.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class JsonRuleParserTest {
    @Test fun UT_CR_5_05_all_rule_types_roundtrip() {
        val rules = listOf<Rule>(
            DnsRule("dns:ads.x.com", "ads.x.com", DomainTag.AD_SDK, RuleLevel.STANDARD, "test"),
            UiRule("global", RuleScope.Global, """[text="跳过"]""", UiAction.CLICK, UiPhase.LAUNCH, "builtin"),
            UiRule("app", RuleScope.App("pkg"), "[checked=true]", UiAction.NOTIFY_AUTORENEW, UiPhase.ANYTIME, "manual"),
            UiRule("page", RuleScope.Page("pkg", "Main"), """[text*="奖励"]""", UiAction.REWARDED_HANDLE, UiPhase.ANYTIME, "learning"),
            NotifyRule("notify", "pkg", null, listOf("优惠", "限时"), "manual"))
        assertEquals(rules, JsonRuleParser.parse(JsonRuleParser.encode(rules)))
        assertEquals(emptyList(), JsonRuleParser.parse(JsonRuleParser.encode(emptyList())))
    }
}
