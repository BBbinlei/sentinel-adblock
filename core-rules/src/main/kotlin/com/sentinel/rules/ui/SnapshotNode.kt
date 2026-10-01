package com.sentinel.rules.ui

import kotlinx.serialization.Serializable

@Serializable data class SnapshotNode(
    override val className: String? = null,
    override val text: String? = null,
    override val desc: String? = null,
    override val viewId: String? = null,
    override val clickable: Boolean = false,
    override val checked: Boolean = false,
    override val bounds: Rect4 = Rect4(0, 0, 0, 0),
    override val children: List<SnapshotNode> = emptyList()
) : NodeView
