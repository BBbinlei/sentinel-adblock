package com.sentinel.a11y.core

import com.sentinel.a11y.fakes.*
import com.sentinel.rules.model.*
import com.sentinel.rules.ui.*
import java.security.MessageDigest
import kotlin.test.*
import org.junit.Test

class LearningRecorderTest {
    private val clock = TestClock(2_000)
    private val recorder = LearningRecorder(clock::read)
    private fun record(c: UiInput.Clicked = clicked(), activity: String? = ACTIVITY,
                       appeared: Long? = 1_000L, auto: Boolean = false) =
        recorder.onClicked(c, activity, appeared, auto)

    private fun assertRule(rule: UiRule, selector: String, scope: RuleScope) {
        assertEquals(selector, rule.selector)
        assertEquals(scope, rule.scope)
        assertEquals(UiAction.CLICK, rule.action)
        assertEquals(UiPhase.ANYTIME, rule.phase)
        assertEquals("learned", rule.source)
        val hash = MessageDigest.getInstance("SHA-1").digest(selector.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }.take(8)
        assertEquals("learn:$APP:$hash", rule.id)
    }

    @Test fun UT_AY_3_01_viewid_has_priority_and_is_stripped() {
        val rule = assertNotNull(record())
        assertRule(rule, """[vid="close"]""", RuleScope.Page(APP, ACTIVITY))
        assertEquals(rule.id, assertNotNull(record()).id)
        assertRule(assertNotNull(record(activity = null)), """[vid="close"]""", RuleScope.App(APP))
    }

    @Test fun UT_AY_3_02_without_viewid_uses_class_short_name_and_text() {
        val rule = assertNotNull(record(clicked(viewId = null)))
        assertRule(rule, """TextView[text="关闭"]""", RuleScope.Page(APP, ACTIVITY))
        val descRule = assertNotNull(record(clicked(text = null, desc = "关闭", viewId = null, className = null)))
        assertRule(descRule, """[desc="关闭"]""", RuleScope.Page(APP, ACTIVITY))
    }

    @Test fun UT_AY_3_03_expired_window_does_not_propose() {
        clock.now = 4_001
        assertNull(record(clicked(ts = clock.now)))
        clock.now = 4_000
        assertNotNull(record(clicked(ts = clock.now))) // <=3000 包含边界。
        assertNull(record(appeared = null))
    }

    @Test fun UT_AY_3_04_non_close_text_does_not_propose() {
        for (text in listOf("立即下载", "确认", "打开", "搜索")) assertNull(record(clicked(text = text)), text)
        assertNull(record(clicked(text = null, desc = null)))
    }

    @Test fun UT_AY_3_05_auto_click_in_same_window_prevents_learning() {
        assertNull(record(auto = true))
        assertNotNull(record(auto = false))
    }

    @Test fun UT_AY_3_06_generated_selectors_parse_and_match_clicked_node() {
        val clicks = listOf(clicked(), clicked(viewId = null),
            clicked(text = null, desc = "关闭", viewId = null, className = null))
        for (c in clicks) {
            val rule = assertNotNull(record(c))
            val node = SnapshotNode(className = c.className, text = c.text, desc = c.desc, viewId = c.viewId)
            val selector = Selector.parse(rule.selector)
            assertSame(node, selector.findFirst(node))
            assertNull(selector.findFirst(SnapshotNode(text = "搜索", desc = "搜索", viewId = "$APP:id/search")))
        }
    }
}
