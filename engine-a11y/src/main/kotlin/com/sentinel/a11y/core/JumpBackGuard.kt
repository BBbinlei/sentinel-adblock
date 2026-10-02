package com.sentinel.a11y.core

import com.sentinel.data.db.EffectiveConfig
import com.sentinel.data.db.ProtectLevel
import com.sentinel.rules.catalog.JumpTargetCatalog

/** reason: "launch" | "shake" */
data class RevertJump(val source: String, val target: String, val reason: String)

class JumpBackGuard(private val clock: () -> Long) {
    private val shakeHints = HashMap<String, Long>()

    fun onShakeHintSeen(pkg: String) { shakeHints[pkg] = clock() }

    /**
     * [tracker] 必须是前台变化之前的状态（含源 App 的 launch 与交互时间）。
     * t.from 为源、t.to 为目标。
     */
    fun onTransition(t: Transition, tracker: ForegroundTracker, cfgOf: (String) -> EffectiveConfig,
                     disabledOf: (String) -> Set<String>, excepted: (String, String) -> Boolean): RevertJump? {
        if (t.from == t.to) return null
        val cfg = cfgOf(t.from)
        if (cfg.level == ProtectLevel.OFF) return null
        if (RULE_ID in disabledOf(t.from)) return null
        if (excepted(t.from, t.to)) return null
        val interaction = tracker.lastInteractionAt(t.from)

        val launch = tracker.launch
        if (cfg.jumpBack && launch != null && launch.pkg == t.from && launch.fromLauncher &&
            t.ts - launch.startedAt <= LAUNCH_WINDOW_MS &&
            (interaction == null || interaction < launch.startedAt) &&
            JumpTargetCatalog.isAdLanding(t.to)) {
            return RevertJump(t.from, t.to, "launch")
        }

        val hintAt = shakeHints[t.from]
        if (cfg.shake && hintAt != null && t.ts - hintAt <= SHAKE_WINDOW_MS &&
            (interaction == null || interaction < hintAt)) {
            return RevertJump(t.from, t.to, "shake")
        }
        return null
    }

    companion object {
        const val RULE_ID = "builtin:jumpback"
        const val LAUNCH_WINDOW_MS = 5_000L
        const val SHAKE_WINDOW_MS = 3_000L
    }
}
