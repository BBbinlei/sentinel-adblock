package com.sentinel.rules.ui

import com.sentinel.rules.model.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class UiRuleIndexTest {
    private fun rule(id: String, scope: RuleScope, selector: String = """[text="关闭"]""") =
        UiRule(id, scope, selector, UiAction.CLICK, UiPhase.ANYTIME, "test")
    @Test fun UT_CR_5_01_lookup_order() {
        val index = UiRuleIndex.build(listOf(rule("global", RuleScope.Global), rule("app", RuleScope.App("pkg")),
            rule("page", RuleScope.Page("pkg", "Main"))))
        assertEquals(listOf("page", "app", "global"), index.lookup("pkg", "Main").map { it.rule.id })
        assertEquals(listOf("app", "global"), index.lookup("pkg", null).map { it.rule.id })
        assertEquals(listOf("global"), index.lookup("other", "Main").map { it.rule.id })
    }
    @Test fun UT_CR_5_02_invalid_count_and_builtins() {
        val index = UiRuleIndex.build(listOf(rule("bad", RuleScope.Global, "[text=")) + BuiltInUiRules.all)
        assertEquals(1, index.invalidCount)
        val compiled = index.lookup("pkg", null)
        assertEquals(3, compiled.size)
        val splash = compiled.first { it.rule.id == "builtin:splash-skip" }
        assertEquals(UiPhase.LAUNCH, splash.rule.phase)
        for (text in listOf("跳过 3s", "跳过广告 5秒", "跳过"))
            assertNotNull(splash.selector.findFirst(SnapshotNode(text = text)), text)
        assertNull(splash.selector.findFirst(SnapshotNode(text = "跳过并下载")))
        assertTrue(BuiltInPatterns.rewardEntry.containsMatchIn("看视频领奖励"))
        assertTrue(BuiltInPatterns.shakeHint.containsMatchIn("摇一摇"))
        assertTrue(BuiltInPatterns.closeLike.matches("暂不"))
        assertTrue(BuiltInPatterns.autoRenew.containsMatchIn("连续包月"))
    }
}
