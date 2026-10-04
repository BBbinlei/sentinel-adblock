package com.sentinel.app.launch

import com.sentinel.rules.ui.SnapshotNode
import com.sentinel.rules.ui.UiRuleIndex
import org.junit.Test
import kotlin.test.*

class YangshipinPopupTest {
    private val close = SnapshotNode(className = "android.widget.ImageView", viewId = "${YangshipinPopup.PACKAGE}:id/iv_close", clickable = true)
    private fun modal(child: SnapshotNode) = SnapshotNode(className = "android.widget.FrameLayout", children = listOf(
        SnapshotNode(className = "android.widget.RelativeLayout", children = listOf(
            SnapshotNode(className = "android.widget.LinearLayout", children = listOf(child))))))
    @Test fun onlyObservedModalCloseMatchesWithinHomePage() {
        val index = UiRuleIndex.build(listOf(YangshipinPopup.rule))
        assertEquals(0, index.invalidCount)
        val compiled = index.lookup(YangshipinPopup.PACKAGE, YangshipinPopup.ACTIVITY).single()
        assertEquals(listOf(close), compiled.selector.findAll(modal(close)))
        assertTrue(compiled.selector.findAll(modal(close.copy(viewId = "${YangshipinPopup.PACKAGE}:id/iv_image"))).isEmpty())
        assertTrue(compiled.selector.findAll(modal(close.copy(viewId = "${YangshipinPopup.PACKAGE}:id/fl_click"))).isEmpty())
        assertTrue(compiled.selector.findAll(SnapshotNode(children = listOf(close))).isEmpty())
        assertTrue(index.lookup(YangshipinPopup.PACKAGE, "VideoDetailActivity").isEmpty())
        assertTrue(index.lookup("other.package", YangshipinPopup.ACTIVITY).isEmpty())
    }
    @Test fun versionRequiresBothExactFields() {
        assertTrue(YangshipinPopup.supported(305030, "3.5.3.26910"))
        assertFalse(YangshipinPopup.supported(305031, "3.5.3.26910"))
        assertFalse(YangshipinPopup.supported(305030, "different"))
    }
    @Test fun realCapturedHierarchySelectsOneCloseAndNoAdTarget() {
        val input = javaClass.getResourceAsStream("/launch/yangshipin-home-popup.xml")!!
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
        fun convert(e: org.w3c.dom.Element): SnapshotNode {
            val children = (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? org.w3c.dom.Element }.map(::convert)
            return SnapshotNode(className = e.getAttribute("class"), text = e.getAttribute("text"),
                viewId = e.getAttribute("resource-id"), clickable = e.getAttribute("clickable") == "true", children = children)
        }
        val matches = UiRuleIndex.build(listOf(YangshipinPopup.rule)).lookup(YangshipinPopup.PACKAGE, YangshipinPopup.ACTIVITY)
            .single().selector.findAll(convert(doc.documentElement))
        assertEquals(1, matches.size)
        assertEquals("${YangshipinPopup.PACKAGE}:id/iv_close", matches.single().viewId)
    }

}
