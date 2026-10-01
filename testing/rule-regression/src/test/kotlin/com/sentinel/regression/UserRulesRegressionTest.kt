package com.sentinel.regression

import com.sentinel.rules.model.*
import com.sentinel.rules.parse.JsonRuleParser
import com.sentinel.rules.ui.*
import java.io.File
import kotlin.test.*
import org.junit.Test

class UserRulesRegressionTest {
    private fun rules(): List<Rule> = JsonRuleParser.parse(File(Fixtures.root, "user-rules.json").readText())
    @Test fun RR_05a_parse_and_compile_selectors() {
        val rules = rules()
        assertTrue(rules.count { it.source == "learning" } >= 10)
        assertTrue(rules.count { it.source == "manual" } >= 10)
        for (rule in rules.filterIsInstance<UiRule>()) Selector.parse(rule.selector)
        assertEquals(rules, JsonRuleParser.parse(JsonRuleParser.encode(rules)))
        assertEquals(JsonRuleParser.encode(rules), File(Fixtures.root, "user-rules.json").readText())
    }
    @Test fun RR_05b_index_valid() {
        val index = UiRuleIndex.build(rules().filterIsInstance<UiRule>())
        assertEquals(0, index.invalidCount)
    }
}
