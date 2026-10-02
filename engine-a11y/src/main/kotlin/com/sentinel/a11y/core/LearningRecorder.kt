package com.sentinel.a11y.core

import com.sentinel.rules.model.RuleScope
import com.sentinel.rules.model.UiAction
import com.sentinel.rules.model.UiPhase
import com.sentinel.rules.model.UiRule
import com.sentinel.rules.ui.BuiltInPatterns
import java.security.MessageDigest

/** 用户在弹窗出现后 3 秒内手动关闭 -> 生成只对该页面生效的候选规则。 */
class LearningRecorder(@Suppress("unused") private val clock: () -> Long) {
    fun onClicked(c: UiInput.Clicked, activity: String?, windowAppearedAt: Long?, autoClickedInWindow: Boolean): UiRule? {
        if (autoClickedInWindow || windowAppearedAt == null) return null
        if (c.ts - windowAppearedAt > WINDOW_MS) return null
        val textClose = c.text?.let(BuiltInPatterns.closeLike::containsMatchIn) == true
        val descClose = c.desc?.let(BuiltInPatterns.closeLike::containsMatchIn) == true
        if (!textClose && !descClose) return null
        val selector = selectorFor(c, textClose) ?: return null
        val scope = if (activity != null) RuleScope.Page(c.pkg, activity) else RuleScope.App(c.pkg)
        return UiRule("learn:${c.pkg}:${sha1Prefix(selector)}", scope, selector, UiAction.CLICK,
            UiPhase.ANYTIME, "learned")
    }

    private fun selectorFor(c: UiInput.Clicked, textClose: Boolean): String? {
        val id = c.viewId?.takeIf { it.isNotEmpty() }
        if (id != null) {
            return if (":id/" in id) """[vid="${quote(id.substringAfter(":id/"))}"]""" else """[id="${quote(id)}"]"""
        }
        if (textClose) {
            val cls = c.className?.substringAfterLast('.')?.takeIf { it.isNotEmpty() && it.all { ch -> ch.isLetterOrDigit() || ch == '_' } }
            return """${cls.orEmpty()}[text="${quote(c.text!!)}"]"""
        }
        return c.desc?.let { """[desc="${quote(it)}"]""" }
    }

    private fun quote(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun sha1Prefix(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }.take(8)

    companion object { const val WINDOW_MS = 3_000L }
}
