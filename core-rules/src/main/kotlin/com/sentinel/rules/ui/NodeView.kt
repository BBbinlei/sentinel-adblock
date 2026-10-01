package com.sentinel.rules.ui

import kotlinx.serialization.Serializable

@Serializable data class Rect4(val l: Int, val t: Int, val r: Int, val b: Int)
interface NodeView {
    val className: String?
    val text: String?
    val desc: String?
    val viewId: String?
    val clickable: Boolean
    val checked: Boolean
    val bounds: Rect4
    val children: List<NodeView>
}
