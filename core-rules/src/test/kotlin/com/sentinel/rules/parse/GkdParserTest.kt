package com.sentinel.rules.parse

import com.sentinel.rules.model.*
import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class GkdParserTest {
    @Test fun UT_CR_5_03_attribute_only_and_page_scope() {
        val sample = """{"apps":[{"id":"pkg","groups":[{"key":7,"rules":[
            {"matches":"@[text=\"关闭\"]","activityIds":["Main","Dialog"]},
            {"matches":"FrameLayout > [text=\"关闭\"]"},
            {"matches":"[text=\"稍后\"]"},
            {"matches":"[text.length<10]"},
            {"matches":["[text=\"关闭\"]","[clickable=true]"]}
        ]}]}]}"""
        val result = GkdParser.parse(sample, "test")
        assertEquals(2, result.skipped)
        assertEquals(4, result.rules.size)
        assertEquals(listOf(RuleScope.Page("pkg", "Main"), RuleScope.Page("pkg", "Dialog")), result.rules.take(2).map { it.scope })
        assertEquals("gkd:pkg:7:0", result.rules[0].id)
        assertEquals("""[text="关闭"]""", result.rules[0].selector)
        assertEquals(RuleScope.App("pkg"), result.rules[2].scope)
        assertTrue(result.rules.all { it.action == UiAction.CLICK && it.phase == UiPhase.ANYTIME && it.source == "test" })
    }
    @Test fun UT_CR_5_04_json5_lexical_conversion() {
        val sample = """// comment
            {apps: [{id: 'pkg', groups: [{key: 1, rules: [
                {matches: '[text="关闭"]',}, /* block */
            ],},],},], url: 'https://example.com/a//b', escaped: 'it\'s',}
        """
        val json = Json.parseToJsonElement(Json5.toJson(sample)).jsonObject
        assertEquals("https://example.com/a//b", json["url"]!!.jsonPrimitive.content)
        assertEquals("it's", json["escaped"]!!.jsonPrimitive.content)
        assertEquals(1, GkdParser.parse(sample, "test").rules.size)
        assertFailsWith<IllegalArgumentException> { Json5.toJson("{ /* unterminated") }
    }
}
