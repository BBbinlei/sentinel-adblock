package com.sentinel.a11y.core

import com.sentinel.data.db.EffectiveConfig
import com.sentinel.data.db.ProtectLevel
import com.sentinel.data.db.RewardedMode
import com.sentinel.rules.model.EventKind
import com.sentinel.rules.model.UiRule
import com.sentinel.rules.ui.BuiltInPatterns
import com.sentinel.rules.ui.NodeView
import com.sentinel.rules.ui.UiRuleIndex

sealed interface A11yAction {
    data class Click(val node: NodeView, val pkg: String, val ruleId: String, val kind: EventKind) : A11yAction
    data class Revert(val jump: RevertJump) : A11yAction
    data class OpenRewardWindow(val pkg: String) : A11yAction
    data class AskRewarded(val pkg: String) : A11yAction
    /** 音量由 VolumeKeeper 实际执行，此动作仅用于记录/诊断。 */
    data class SetMuted(val muted: Boolean) : A11yAction
    data class WarnAutoRenew(val pkg: String) : A11yAction
    data class ProposeRule(val rule: UiRule) : A11yAction
    data class Signal(val signal: HealthSignal) : A11yAction
}

/** 由服务从数据层快照提供。 */
interface A11yState {
    fun cfg(pkg: String): EffectiveConfig
    fun disabled(pkg: String): Set<String>
    fun excepted(src: String, tgt: String): Boolean
    fun index(): UiRuleIndex
}

class A11yBrain(
    private val state: A11yState,
    private val rewarded: RewardedHandler,
    private val clock: () -> Long,
    private val ignored: () -> Set<String>,
    private val launchers: () -> Set<String>,
    labelToPkg: (String) -> String?,
    private val onError: (Throwable) -> Unit = {},
) {
    private val tracker = ForegroundTracker(ignored, launchers)
    private val guard = JumpBackGuard(clock)
    private val health = HealthSignals(clock, labelToPkg)
    private val recorder = LearningRecorder(clock)
    private val throttle = ClickThrottle(clock)

    private val windowKey = HashMap<String, String?>()
    private val appearedAt = HashMap<String, Long>()
    private val autoClickAt = HashMap<String, Long>()
    private val warnedAt = HashMap<String, Long>()

    /** 服务据此决定是否需要获取节点树。 */
    fun wantsTree(pkg: String, activity: String?): Boolean = try {
        when {
            pkg == HealthSignals.SYSTEM_PKG -> true
            pkg in ignored() -> false
            state.cfg(pkg).level == ProtectLevel.OFF -> false
            rewarded.active -> true
            state.cfg(pkg).shake -> true
            else -> state.index().lookup(pkg, activity).isNotEmpty()
        }
    } catch (e: Exception) { onError(e); false }

    fun onInput(i: UiInput, root: NodeView?): List<A11yAction> {
        val actions = mutableListOf<A11yAction>()
        try {
            when (i) {
                is UiInput.Interaction -> tracker.onInput(i)
                is UiInput.Clicked -> onClicked(i, actions)
                is UiInput.WindowChanged -> onWindow(i, root, actions)
            }
        } catch (e: Exception) {
            onError(e)
        }
        return actions
    }

    fun onTick(): List<A11yAction> = try {
        if (rewarded.onTick() == RewardedAction.Finished) listOf(A11yAction.SetMuted(false)) else emptyList()
    } catch (e: Exception) { onError(e); emptyList() }

    fun onRewardedChoice(silent: Boolean) {
        try { rewarded.onUserChoice(silent) } catch (e: Exception) { onError(e) }
    }

    private fun onClicked(c: UiInput.Clicked, out: MutableList<A11yAction>) {
        tracker.onInput(c)
        if (c.pkg in ignored()) return
        val cfg = state.cfg(c.pkg)
        if (cfg.level == ProtectLevel.OFF) return
        if (rewarded.onClicked(c, cfg.rewarded) == RewardedAction.OpenRewardWindow) {
            out += A11yAction.OpenRewardWindow(c.pkg)
        }
        val appeared = appearedAt[c.pkg]
        val auto = appeared != null && (autoClickAt[c.pkg] ?: Long.MIN_VALUE) >= appeared
        recorder.onClicked(c, tracker.currentActivity, appeared, auto)?.let { out += A11yAction.ProposeRule(it) }
    }

    private fun onWindow(w: UiInput.WindowChanged, root: NodeView?, out: MutableList<A11yAction>) {
        val before = tracker.copy()
        val transition = tracker.onInput(w)
        noteWindow(w)
        val isIgnored = w.pkg in ignored()

        if (transition != null) {
            guard.onTransition(transition, before, state::cfg, state::disabled, state::excepted)
                ?.let { out += A11yAction.Revert(it) }
            health.onLaunch(transition.to, tracker.launchesWithin(transition.to, LOOP_WINDOW_MS, w.ts))
                ?.let { out += A11yAction.Signal(it) }
        }
        if (!isIgnored || w.pkg in launchers()) {
            if (rewarded.onForeground(w.pkg) == RewardedAction.Finished) out += A11yAction.SetMuted(false)
        }
        if (root == null) return

        if (w.pkg == HealthSignals.SYSTEM_PKG) {
            health.onWindowText(w.pkg, texts(root))?.let { out += A11yAction.Signal(it) }
            return
        }
        if (isIgnored) return
        val cfg = state.cfg(w.pkg)
        if (cfg.level == ProtectLevel.OFF) return

        if (hasHint(root)) guard.onShakeHintSeen(w.pkg)

        when (val action = rewarded.onContent(w.pkg, root)) {
            is RewardedAction.ClickClose -> {
                autoClickAt[w.pkg] = w.ts
                out += A11yAction.Click(action.node, w.pkg, REWARDED_CLOSE_RULE_ID, EventKind.REWARDED_SILENCED)
                return
            }
            else -> Unit
        }

        val launch = tracker.launch
        val inLaunch = launch != null && launch.pkg == w.pkg && launch.fromLauncher &&
            w.ts - launch.startedAt <= LAUNCH_WINDOW_MS
        val launchKey = "${w.pkg}:${launch?.startedAt ?: 0}"
        when (val d = RuleClicker.decide(root, w.pkg, tracker.currentActivity ?: w.activity, inLaunch, cfg,
            state.disabled(w.pkg), state.index(), throttle, launchKey)) {
            is ClickDecision.Click -> {
                autoClickAt[w.pkg] = w.ts
                out += A11yAction.Click(d.node, w.pkg, d.ruleId, d.kind)
            }
            is ClickDecision.RewardedPage -> onRewardedPage(w.pkg, cfg, out)
            is ClickDecision.WarnAutoRenew -> {
                val last = warnedAt[w.pkg]
                if (last == null || w.ts - last >= WARN_INTERVAL_MS) {
                    warnedAt[w.pkg] = w.ts
                    out += A11yAction.WarnAutoRenew(w.pkg)
                }
            }
            null -> Unit
        }
    }

    private fun onRewardedPage(pkg: String, cfg: EffectiveConfig, out: MutableList<A11yAction>) {
        val wasActive = rewarded.active
        when (rewarded.onRewardedPage(pkg, cfg.rewarded)) {
            RewardedAction.AskUser -> out += A11yAction.AskRewarded(pkg)
            else -> if (!wasActive && rewarded.active && cfg.rewarded == RewardedMode.SILENT) out += A11yAction.SetMuted(true)
        }
    }

    /** 记录窗口「出现」时间：换了 Activity 或弹窗窗口才算新出现，同一页面的内容变化不刷新。 */
    private fun noteWindow(w: UiInput.WindowChanged) {
        val known = windowKey.containsKey(w.pkg)
        if (!known || w.activity == null || windowKey[w.pkg] != w.activity) appearedAt[w.pkg] = w.ts
        if (w.activity != null) windowKey[w.pkg] = w.activity else if (!known) windowKey[w.pkg] = null
    }

    private fun hasHint(n: NodeView): Boolean =
        n.text?.let(BuiltInPatterns.shakeHint::containsMatchIn) == true ||
            n.desc?.let(BuiltInPatterns.shakeHint::containsMatchIn) == true || n.children.any { hasHint(it) }

    private fun texts(n: NodeView, acc: MutableList<String> = mutableListOf()): List<String> {
        n.text?.let { acc += it }
        n.desc?.let { acc += it }
        n.children.forEach { texts(it, acc) }
        return acc
    }

    companion object {
        const val REWARDED_CLOSE_RULE_ID = "builtin:rewarded-close"
        const val LAUNCH_WINDOW_MS = 5_000L
        const val LOOP_WINDOW_MS = 60_000L
        const val WARN_INTERVAL_MS = 600_000L
    }
}
