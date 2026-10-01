package com.sentinel.a11y.fakes

import com.sentinel.a11y.core.*
import com.sentinel.data.db.ProtectLevel
import com.sentinel.data.db.RewardedMode
import com.sentinel.data.db.EffectiveConfig
import com.sentinel.rules.model.*
import com.sentinel.rules.ui.*
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

const val APP = "com.example.reader"
const val HOME = "com.example.launcher"
const val IME = "com.example.ime"
const val SYSTEM_UI = "com.android.systemui"
const val TAOBAO = "com.taobao.taobao"
const val PINDUODUO = "com.xunmeng.pinduoduo"
const val ACTIVITY = "com.example.reader.MainActivity"

class TestClock(var now: Long = 1_000L) {
    fun read(): Long = now
}

fun config(pkg: String = APP, level: ProtectLevel = ProtectLevel.STANDARD,
           splash: Boolean = true, rewarded: RewardedMode = RewardedMode.SILENT,
           shake: Boolean = true, jumpBack: Boolean = true, observing: Boolean = false) =
    EffectiveConfig(pkg, level, observing, splash, rewarded, shake, jumpBack,
        notify = true, limitOverlay = false, denyClipboard = false)

fun clicked(pkg: String = APP, ts: Long = 2_000L, text: String? = "关闭",
            desc: String? = null, viewId: String? = "$APP:id/close",
            className: String? = "android.widget.TextView") =
    UiInput.Clicked(pkg, text, desc, viewId, className, ts)

fun window(pkg: String, ts: Long, activity: String? = "$pkg.MainActivity") =
    UiInput.WindowChanged(pkg, activity, ts)

fun tracker() = ForegroundTracker(
    ignored = { setOf("com.sentinel.adblock", SYSTEM_UI, IME, HOME) },
    launchers = { setOf(HOME) }
)

fun rule(id: String = "test:close", selector: String = """[text="关闭"]""",
         action: UiAction = UiAction.CLICK, phase: UiPhase = UiPhase.ANYTIME,
         scope: RuleScope = RuleScope.App(APP)) =
    UiRule(id, scope, selector, action, phase, "test")

// 手工合成的小型页面；XML 仅为测试资源格式，不是产品侧快照格式。
fun snapshot(name: String): SnapshotNode {
    val stream = checkNotNull(TestClock::class.java.getResourceAsStream("/snapshots/$name.xml"))
    val document = stream.use { DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(it) }
    fun read(e: Element): SnapshotNode {
        fun attr(key: String) = if (e.hasAttribute(key)) e.getAttribute(key) else null
        val children = (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }.map(::read)
        return SnapshotNode(className = attr("class"), text = attr("text"), desc = attr("desc"),
            viewId = attr("id"), clickable = attr("clickable") == "true", checked = attr("checked") == "true",
            children = children)
    }
    return read(document.documentElement)
}

class FakeVolume(var value: Int = 7) : VolumePort {
    val writes = mutableListOf<Int>()
    override fun get() = value
    override fun set(v: Int) { value = v; writes += v }
}

class MemoryStore : KeyValueStore {
    val values = mutableMapOf<String, Int>()
    override fun getInt(k: String) = values[k]
    override fun putInt(k: String, v: Int) { values[k] = v }
    override fun remove(k: String) { values.remove(k) }
}
