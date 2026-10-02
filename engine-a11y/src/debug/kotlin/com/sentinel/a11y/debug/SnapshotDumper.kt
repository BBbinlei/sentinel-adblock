package com.sentinel.a11y.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sentinel.a11y.service.NodeViewAdapter
import com.sentinel.a11y.service.SentinelAccessibilityService
import com.sentinel.rules.ui.NodeView
import com.sentinel.rules.ui.SnapshotNode
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 调试版快照录制：把当前活动窗口的节点树写成 SnapshotNode JSON。
 * 输出：<外部 files 目录>/snapshots/<包名>/<name>.json（即 /sdcard/Android/data/<应用包名>/files/snapshots/...）。
 */
class SnapshotDumper : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        try {
            val name = intent.getStringExtra("name")
            if (name == null || !NAME.matches(name)) { Log.w(TAG, "快照名称无效"); return }
            val root = SentinelAccessibilityService.current?.rootInActiveWindow
            if (root == null) { Log.w(TAG, "无障碍服务未连接或无活动窗口"); return }
            val pkg = root.packageName?.toString()
            if (pkg == null || !PKG.matches(pkg)) { Log.w(TAG, "窗口包名无效"); return }
            val dir = File(requireNotNull(context.getExternalFilesDir(null)), "snapshots/$pkg").apply { mkdirs() }
            val file = File(dir, "$name.json")
            if (file.exists()) { Log.w(TAG, "同名快照已存在: $file"); return }
            file.writeText(Json.encodeToString(SnapshotNode.serializer(), toSnapshot(NodeViewAdapter(root))))
            Log.i(TAG, "已保存快照: $file")
        } catch (e: Exception) { Log.w(TAG, "快照录制失败", e) }
    }

    private fun toSnapshot(n: NodeView): SnapshotNode = SnapshotNode(n.className, n.text, n.desc, n.viewId,
        n.clickable, n.checked, n.bounds, n.children.map(::toSnapshot))

    private companion object {
        const val ACTION = "com.sentinel.a11y.DEBUG_DUMP"
        const val TAG = "SentinelA11yDump"
        val NAME = Regex("[A-Za-z0-9][A-Za-z0-9_-]*")
        val PKG = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}
