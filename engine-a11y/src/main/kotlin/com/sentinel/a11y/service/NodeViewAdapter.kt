package com.sentinel.a11y.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.sentinel.rules.ui.NodeView
import com.sentinel.rules.ui.Rect4

/** 把 AccessibilityNodeInfo 适配成纯 Kotlin 的 NodeView；子节点惰性读取。viewId 保留完整资源名。 */
class NodeViewAdapter(val info: AccessibilityNodeInfo) : NodeView {
    override val className: String? get() = info.className?.toString()
    override val text: String? get() = info.text?.toString()
    override val desc: String? get() = info.contentDescription?.toString()
    override val viewId: String? get() = info.viewIdResourceName
    override val clickable: Boolean get() = info.isClickable
    override val checked: Boolean get() = info.isChecked
    override val bounds: Rect4
        get() {
            val r = Rect()
            info.getBoundsInScreen(r)
            return Rect4(r.left, r.top, r.right, r.bottom)
        }
    override val children: List<NodeView> by lazy {
        buildList {
            for (i in 0 until info.childCount) {
                val child = try { info.getChild(i) } catch (_: Exception) { null } ?: continue
                add(NodeViewAdapter(child))
            }
        }
    }
}
