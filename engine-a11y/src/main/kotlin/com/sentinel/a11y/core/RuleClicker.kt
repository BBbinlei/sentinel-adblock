package com.sentinel.a11y.core

import com.sentinel.data.db.EffectiveConfig
import com.sentinel.data.db.ProtectLevel
import com.sentinel.rules.model.EventKind
import com.sentinel.rules.model.UiAction
import com.sentinel.rules.model.UiPhase
import com.sentinel.rules.ui.BuiltInPatterns
import com.sentinel.rules.ui.NodeView
import com.sentinel.rules.ui.UiRuleIndex

sealed interface ClickDecision {
    /** kind: SPLASH_SKIPPED / POPUP_CLOSED */
    data class Click(val node: NodeView, val ruleId: String, val kind: EventKind) : ClickDecision
    data class WarnAutoRenew(val ruleId: String) : ClickDecision
    data class RewardedPage(val ruleId: String) : ClickDecision
}

object RuleClicker {
    val trapWords = Regex("下载|安装|确认|立即|打开|去看看")

    fun decide(root: NodeView, pkg: String, activity: String?, inLaunchWindow: Boolean, cfg: EffectiveConfig,
               disabled: Set<String>, index: UiRuleIndex, throttle: ClickThrottle, launchKey: String): ClickDecision? {
        if (cfg.level == ProtectLevel.OFF) return null
        for (compiled in index.lookup(pkg, activity)) {
            val rule = compiled.rule
            if (rule.id in disabled) continue
            when (rule.action) {
                UiAction.CLICK -> {
                    if (rule.phase == UiPhase.LAUNCH && !(inLaunchWindow && cfg.splash)) continue
                    val node = compiled.selector.findAll(root).firstOrNull { !isTrap(it) } ?: continue
                    if (!throttle.allow(launchKey, rule.id)) continue
                    val kind = if (rule.phase == UiPhase.LAUNCH) EventKind.SPLASH_SKIPPED else EventKind.POPUP_CLOSED
                    return ClickDecision.Click(node, rule.id, kind)
                }
                UiAction.REWARDED_HANDLE ->
                    if (compiled.selector.findFirst(root) != null) return ClickDecision.RewardedPage(rule.id)
                UiAction.NOTIFY_AUTORENEW ->
                    if (compiled.selector.findFirst(root) != null && hasText(root, BuiltInPatterns.autoRenew))
                        return ClickDecision.WarnAutoRenew(rule.id)
            }
        }
        return null
    }

    private fun isTrap(n: NodeView) =
        n.text?.let(trapWords::containsMatchIn) == true || n.desc?.let(trapWords::containsMatchIn) == true

    private fun hasText(n: NodeView, re: Regex): Boolean =
        n.text?.let(re::containsMatchIn) == true || n.children.any { hasText(it, re) }
}
