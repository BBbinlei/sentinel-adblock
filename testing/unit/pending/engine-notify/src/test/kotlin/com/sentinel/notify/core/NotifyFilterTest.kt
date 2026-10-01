package com.sentinel.notify.core

import com.sentinel.data.db.ProtectLevel
import com.sentinel.notify.fakes.MARKET_PKG
import com.sentinel.notify.fakes.SELF_PKG
import com.sentinel.notify.fakes.config
import com.sentinel.rules.model.NotifyRule
import kotlin.test.*
import org.junit.Test

class NotifyFilterTest {
    private val marketing = PostedNotification(MARKET_PKG, "marketing", "限时福利", "今日活动", false, "market:1")

    // UT-NT-1-01
    @Test fun UT_NT_1_01_marketing_matches_built_in_rule() {
        val packages = setOf(
            MARKET_PKG, "com.heytap.themestore", "com.nearme.gamecenter",
            "com.heytap.browser", "com.heytap.quicksearchbox", "com.coloros.assistantscreen",
        )
        val keywords = listOf("限时", "福利", "领取", "优惠", "红包", "热门", "免费", "推荐")
        assertEquals(6, BuiltInNotifyRules.all.size)
        assertEquals(packages, BuiltInNotifyRules.all.map { it.pkg }.toSet())
        for (rule in BuiltInNotifyRules.all) {
            assertEquals("builtin:notify:${rule.pkg}", rule.id)
            assertNull(rule.channelId)
            assertEquals(keywords.toSet(), rule.keywords.toSet())
            assertEquals(keywords.size, rule.keywords.size)
            assertEquals(rule, NotifyFilter.decide(marketing.copy(pkg = assertNotNull(rule.pkg)), SELF_PKG,
                config(assertNotNull(rule.pkg)), BuiltInNotifyRules.all, emptySet()))
        }
    }

    // UT-NT-1-02
    @Test fun UT_NT_1_02_update_completed_is_not_marketing() {
        assertNull(NotifyFilter.decide(marketing.copy(title = "更新完成", text = "应用已更新至最新版本"),
            SELF_PKG, config(MARKET_PKG), BuiltInNotifyRules.all, emptySet()))
    }

    // UT-NT-1-03
    @Test fun UT_NT_1_03_ongoing_notification_is_untouched() {
        assertNotNull(NotifyFilter.decide(marketing, SELF_PKG, config(MARKET_PKG), BuiltInNotifyRules.all, emptySet()))
        assertNull(NotifyFilter.decide(marketing.copy(ongoing = true), SELF_PKG,
            config(MARKET_PKG), BuiltInNotifyRules.all, emptySet()))
    }

    // UT-NT-1-04
    @Test fun UT_NT_1_04_off_or_notify_disabled_is_untouched() {
        for (cfg in listOf(config(MARKET_PKG, level = ProtectLevel.OFF), config(MARKET_PKG, notify = false))) {
            assertNull(NotifyFilter.decide(marketing, SELF_PKG, cfg, BuiltInNotifyRules.all, emptySet()))
        }
        assertNotNull(NotifyFilter.decide(marketing, SELF_PKG,
            config(MARKET_PKG), BuiltInNotifyRules.all, emptySet()))
    }

    // UT-NT-1-05
    @Test fun UT_NT_1_05_disabled_and_invalid_rules_are_skipped_in_order() {
        val builtIn = BuiltInNotifyRules.all.single { it.pkg == MARKET_PKG }
        assertNull(NotifyFilter.decide(marketing, SELF_PKG, config(MARKET_PKG),
            BuiltInNotifyRules.all, setOf(builtIn.id)))
        val invalid = NotifyRule("invalid", null, null, emptyList(), "test")
        val fallback = builtIn.copy(id = "user:fallback")
        val last = fallback.copy(id = "user:last")
        assertEquals(fallback, NotifyFilter.decide(marketing, SELF_PKG, config(MARKET_PKG),
            listOf(invalid, builtIn, fallback, last), setOf(builtIn.id)))
    }

    // UT-NT-1-06
    @Test fun UT_NT_1_06_own_notification_is_untouched() {
        val ownRule = NotifyRule("own", SELF_PKG, null, listOf("福利"), "test")
        assertNull(NotifyFilter.decide(marketing.copy(pkg = SELF_PKG), SELF_PKG,
            config(SELF_PKG), listOf(ownRule), emptySet()))
        assertEquals(ownRule, NotifyFilter.decide(marketing.copy(pkg = SELF_PKG), "another.app",
            config(SELF_PKG), listOf(ownRule), emptySet()))
    }
}
