package com.sentinel.rules.notify

import com.sentinel.rules.model.NotifyRule
import kotlin.test.*
import org.junit.jupiter.api.Test

class NotifyMatcherTest {
    private val rule = NotifyRule("n", "pkg", "marketing", emptyList(), "test")
    @Test fun UT_CR_6_01_package_channel() {
        assertTrue(NotifyMatcher.matches(rule, "pkg", "marketing", null, null))
        assertFalse(NotifyMatcher.matches(rule, "pkg", "service", null, null))
        assertFalse(NotifyMatcher.matches(rule, "pkg", null, null, null))
    }
    @Test fun UT_CR_6_02_keyword_in_body() {
        val keyword = rule.copy(channelId = null, keywords = listOf("优惠", "限时"))
        assertTrue(NotifyMatcher.matches(keyword, "pkg", null, null, "今日限时折扣"))
        assertTrue(NotifyMatcher.matches(keyword, "pkg", "any", "优惠已到", null))
        assertFalse(NotifyMatcher.matches(keyword, "pkg", null, null, null))
        assertFalse(NotifyMatcher.matches(keyword, "pkg", null, "验证", "验证码"))
        assertTrue(NotifyMatcher.matches(keyword.copy(pkg = null), "other", null, "优惠", null))
    }
    @Test fun UT_CR_6_03_empty_rule_invalid() {
        val empty = rule.copy(pkg = null, channelId = null)
        assertFalse(NotifyMatcher.isValid(empty))
        assertFalse(NotifyMatcher.matches(empty, "pkg", null, null, null))
    }
    @Test fun UT_CR_6_04_other_app() {
        assertFalse(NotifyMatcher.matches(rule, "other", "marketing", "优惠", "限时"))
    }
}
