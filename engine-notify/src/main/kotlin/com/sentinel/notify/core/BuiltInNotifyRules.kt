package com.sentinel.notify.core

import com.sentinel.rules.model.NotifyRule

object BuiltInNotifyRules {
    val all: List<NotifyRule> = listOf(
        "com.heytap.market", "com.heytap.themestore", "com.nearme.gamecenter",
        "com.heytap.browser", "com.heytap.quicksearchbox", "com.coloros.assistantscreen",
    ).map { pkg -> NotifyRule("builtin:notify:$pkg", pkg, null,
        listOf("限时", "福利", "领取", "优惠", "红包", "热门", "免费", "推荐"), "builtin") }
}
