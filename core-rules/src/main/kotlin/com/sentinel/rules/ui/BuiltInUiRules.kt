package com.sentinel.rules.ui

import com.sentinel.rules.model.*

object BuiltInUiRules {
    val all: List<UiRule> = listOf(
        UiRule("builtin:splash-skip", RuleScope.Global,
            """[text~="^\s*跳过(广告)?\s*\d{0,2}\s*[sS秒]?\s*$"]""", UiAction.CLICK, UiPhase.LAUNCH, "builtin"),
        UiRule("builtin:rewarded", RuleScope.Global,
            """[text~="奖励将于\d+秒后发放|\d+\s*秒后可领取奖励|已获得奖励|恭喜获得奖励"]""",
            UiAction.REWARDED_HANDLE, UiPhase.ANYTIME, "builtin"),
        UiRule("builtin:autorenew", RuleScope.Global, "[checked=true]",
            UiAction.NOTIFY_AUTORENEW, UiPhase.ANYTIME, "builtin")
    )
}
