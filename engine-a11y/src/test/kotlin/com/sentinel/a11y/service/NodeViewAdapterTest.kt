package com.sentinel.a11y.service

import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.sentinel.rules.ui.Rect4
import com.sentinel.rules.ui.Selector
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class NodeViewAdapterTest {
    @Test fun UT_AY_8_01_android_tree_maps_all_node_fields() {
        val context = RuntimeEnvironment.getApplication()
        val root = AccessibilityNodeInfo.obtain(View(context))
        val child = AccessibilityNodeInfo.obtain(View(context))
        val grandchild = AccessibilityNodeInfo.obtain(View(context))
        root.className = "android.widget.FrameLayout"
        root.text = "容器"
        root.contentDescription = "页面"
        root.viewIdResourceName = "com.example.reader:id/container"
        root.setBoundsInScreen(Rect(10, 20, 300, 400))
        child.className = "android.widget.CheckBox"
        child.text = "关闭"
        child.contentDescription = "关闭弹窗"
        child.viewIdResourceName = "com.example.reader:id/close"
        child.isClickable = true
        child.isChecked = true
        child.setBoundsInScreen(Rect(20, 30, 80, 90))
        grandchild.className = "android.widget.TextView"
        grandchild.text = "子节点"
        grandchild.setBoundsInScreen(Rect(21, 31, 40, 50))
        shadowOf(root).addChild(child)
        shadowOf(child).addChild(grandchild)

        // PLAN 只给文件名，未给适配器签名；构造器假设见 README。
        val node = NodeViewAdapter(root)
        assertEquals("android.widget.FrameLayout", node.className)
        assertEquals("容器", node.text)
        assertEquals("页面", node.desc)
        assertEquals("com.example.reader:id/container", node.viewId)
        assertEquals(Rect4(10, 20, 300, 400), node.bounds)
        assertFalse(node.clickable)
        assertFalse(node.checked)
        val mappedChild = node.children.single()
        assertEquals("android.widget.CheckBox", mappedChild.className)
        assertEquals("关闭", mappedChild.text)
        assertEquals("关闭弹窗", mappedChild.desc)
        assertEquals("com.example.reader:id/close", mappedChild.viewId)
        assertTrue(mappedChild.clickable)
        assertTrue(mappedChild.checked)
        assertEquals(Rect4(20, 30, 80, 90), mappedChild.bounds)
        // vid 的截取由现有 Selector 完成；NodeView.viewId 必须保留完整资源名。
        assertSame(mappedChild, Selector.parse("""[vid="close"]""").findFirst(mappedChild))
        val mappedGrandchild = mappedChild.children.single()
        assertEquals("android.widget.TextView", mappedGrandchild.className)
        assertEquals("子节点", mappedGrandchild.text)
        assertNull(mappedGrandchild.desc)
        assertNull(mappedGrandchild.viewId)
        assertEquals(Rect4(21, 31, 40, 50), mappedGrandchild.bounds)
        assertTrue(mappedGrandchild.children.isEmpty())
    }
}
