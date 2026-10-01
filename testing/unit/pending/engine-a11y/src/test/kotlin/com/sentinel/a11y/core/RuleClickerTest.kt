package com.sentinel.a11y.core

import com.sentinel.a11y.fakes.*
import com.sentinel.data.db.ProtectLevel
import com.sentinel.data.policy.EffectiveConfig
import com.sentinel.rules.model.*
import com.sentinel.rules.ui.*
import kotlin.test.*
import org.junit.Test

class RuleClickerTest {
    private fun decide(root: NodeView, launch: Boolean = true, cfg: EffectiveConfig = config(),
                       disabled: Set<String> = emptySet(), rules: List<UiRule> = BuiltInUiRules.all) =
        RuleClicker.decide(root, APP, ACTIVITY, launch, cfg, disabled, UiRuleIndex.build(rules),
            ClickThrottle { 1_000L }, "$APP:1000")

    @Test fun UT_AY_2_01_splash_click_in_launch_window() {
        for (name in listOf("splash-1", "splash-2", "splash-3")) {
            val root = snapshot(name)
            val click = assertIs<ClickDecision.Click>(decide(root))
            assertEquals(EventKind.SPLASH_SKIPPED, click.kind)
            assertEquals("builtin:splash-skip", click.ruleId)
            assertSame(root.children.single(), click.node)
            assertIs<ClickDecision.Click>(decide(root, cfg = config(observing = true)))
        }
    }

    @Test fun UT_AY_2_02_launch_rules_do_not_run_outside_window() {
        for (name in listOf("splash-1", "splash-2", "splash-3")) assertNull(decide(snapshot(name), launch = false))
        for (name in listOf("popup-1", "popup-2", "popup-3")) {
            val root = snapshot(name)
            val click = assertIs<ClickDecision.Click>(decide(root, launch = false,
                rules = listOf(rule(selector = """[vid="close"]"""))))
            assertEquals(EventKind.POPUP_CLOSED, click.kind)
            assertSame(root.children.single(), click.node)
        }
    }

    @Test fun UT_AY_2_03_splash_toggle_and_off_level() {
        assertNull(decide(snapshot("splash-1"), cfg = config(splash = false)))
        assertNull(decide(snapshot("splash-1"), cfg = config(level = ProtectLevel.OFF)))
        assertNull(decide(snapshot("popup-1"), cfg = config(level = ProtectLevel.OFF), rules = listOf(rule())))
    }

    @Test fun UT_AY_2_04_trap_text_is_never_clicked() {
        val broadRule = rule(selector = """[clickable=true]""")
        for (name in listOf("trap-1", "trap-2")) assertNull(decide(snapshot(name), rules = listOf(broadRule)))
        for (word in listOf("下载", "安装", "确认", "立即", "打开", "去看看")) {
            assertNull(decide(SnapshotNode(text = word, clickable = true), rules = listOf(broadRule)), word)
        }
    }

    @Test fun UT_AY_2_05_disabled_rule_is_skipped_with_fallback() {
        val root = snapshot("popup-1")
        val page = rule("page", scope = RuleScope.Page(APP, ACTIVITY))
        val app = rule("app")
        val rules = listOf(app, page)
        assertEquals("page", assertIs<ClickDecision.Click>(decide(root, rules = rules)).ruleId)
        assertEquals("app", assertIs<ClickDecision.Click>(decide(root, disabled = setOf("page"), rules = rules)).ruleId)
        assertNull(decide(root, disabled = setOf("page", "app"), rules = rules))
    }

    @Test fun UT_AY_2_06_normal_pages_have_no_action() {
        for (name in listOf("normal-1", "normal-2", "normal-3")) {
            assertNull(decide(snapshot(name), rules = BuiltInUiRules.all + rule()))
        }
    }

    @Test fun UT_AY_2_07_throttle_interval_limit_and_reset() {
        val clock = TestClock()
        val throttle = ClickThrottle(clock::read)
        val index = UiRuleIndex.build(BuiltInUiRules.all)
        val root = snapshot("splash-1")
        fun decide(key: String = "launch-1") = RuleClicker.decide(root, APP, ACTIVITY, true,
            config(), emptySet(), index, throttle, key)
        assertIs<ClickDecision.Click>(decide())
        clock.now += 999
        assertNull(decide())
        clock.now += 1
        assertIs<ClickDecision.Click>(decide())
        clock.now += 1_000
        assertIs<ClickDecision.Click>(decide())
        clock.now += 1_000
        assertNull(decide())
        assertIs<ClickDecision.Click>(decide("launch-2"))
        assertTrue(throttle.allow("launch-1", "different-rule"))
        throttle.reset("launch-1")
        assertIs<ClickDecision.Click>(decide())
    }

    @Test fun UT_AY_2_08_checked_autorenew_warns_only_with_matching_text() {
        val warning = assertIs<ClickDecision.WarnAutoRenew>(decide(snapshot("autorenew-1")))
        assertEquals("builtin:autorenew", warning.ruleId)
        assertNull(decide(SnapshotNode(text = "已阅读用户协议", checked = true)))
        assertNull(decide(SnapshotNode(text = "自动续费", checked = false)))
        assertIs<ClickDecision.WarnAutoRenew>(decide(SnapshotNode(text = "连续包月", checked = true)))
    }
}
