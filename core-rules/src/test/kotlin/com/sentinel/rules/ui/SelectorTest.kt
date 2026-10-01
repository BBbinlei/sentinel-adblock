package com.sentinel.rules.ui

import kotlinx.serialization.json.Json
import kotlin.test.*
import org.junit.jupiter.api.Test

class SelectorTest {
    private fun node(text: String = "关闭", children: List<SnapshotNode> = emptyList()) =
        SnapshotNode(className = "android.widget.TextView", text = text, children = children)
    @Test fun UT_CR_4_01_operators() {
        val target = node("跳过 3")
        for (selector in listOf("""[text~="^跳过\s*\d+"]""", """[text="跳过 3"]""",
            """[text^="跳过"]""", """[text$="3"]""", """[text*="过 "]"""))
            assertSame(target, Selector.parse(selector).findFirst(target), selector)
        val button = target.copy(viewId = "pkg:id/skip", clickable = true, checked = true, desc = "跳过")
        assertSame(button, Selector.parse("""[vid="skip"][id^="pkg:"][clickable=true][checked=true][desc="跳过"]""").findFirst(button))
        assertNull(Selector.parse("""[text="不存在"]""").findFirst(target))
    }
    @Test fun UT_CR_4_02_child_and_descendant() {
        val direct = node()
        val nested = node()
        val root = SnapshotNode(className = "android.widget.FrameLayout",
            children = listOf(direct, SnapshotNode(className = "android.widget.LinearLayout", children = listOf(nested))))
        assertEquals(listOf(direct), Selector.parse("""FrameLayout > TextView[text="关闭"]""").findAll(root))
        val found = Selector.parse("""FrameLayout TextView[text="关闭"]""").findAll(root)
        assertEquals(2, found.size)
        assertSame(direct, found[0])
        assertSame(nested, found[1])
    }
    @Test fun UT_CR_4_03_class_short_name_and_snapshot() {
        val target = node()
        assertSame(target, Selector.parse("TextView").findFirst(target))
        assertEquals(target, Json.decodeFromString<SnapshotNode>(Json.encodeToString(target)))
    }
    @Test fun UT_CR_4_04_syntax_errors() {
        for (text in listOf("[text=", "", "[unknown=true]", "TextView >", """[text~="("]"""))
            assertFailsWith<SelectorSyntaxException>(text) { Selector.parse(text) }
    }
}
