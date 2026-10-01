package com.sentinel.guard.policy

import com.sentinel.data.db.AppConfigEntity
import com.sentinel.data.db.SignalEntity
import com.sentinel.data.db.SignalKind

sealed interface Decision {
    data class DisableRules(val pkg: String, val ruleIds: List<String>, val reason: String) : Decision
    data class NoRuleFound(val pkg: String, val reason: String) : Decision
    data class ExtendObservation(val pkg: String) : Decision
    data class EndObservation(val pkg: String) : Decision
}

/** Pure decision logic: no android.* references. */
object GuardPolicy {
    private const val CRASH_WINDOW_MS = 120_000L
    private const val UNDO_WINDOW_MS = 600_000L
    private const val UNDO_REPEAT_THRESHOLD = 2

    // Exhaustive over SignalKind (no else): a new kind must fail compilation here.
    fun hitWindowMs(kind: SignalKind): Long = when (kind) {
        SignalKind.CRASH_DIALOG, SignalKind.COLD_START_LOOP, SignalKind.DROPBOX_CRASH -> CRASH_WINDOW_MS
        SignalKind.USER_UNDO, SignalKind.TEMP_ALLOW, SignalKind.RETRY_STORM -> UNDO_WINDOW_MS
    }

    fun onSignal(signal: SignalEntity, recentHits: List<String>, sameKindCount24h: Int): Decision? {
        val pkg = signal.pkg
        val hits = recentHits.distinct()
        fun fromHits(reason: String): Decision =
            if (hits.isNotEmpty()) Decision.DisableRules(pkg, hits, reason) else Decision.NoRuleFound(pkg, reason)
        val ruleId = signal.ruleId
        return when (signal.kind) {
            SignalKind.RETRY_STORM ->
                if (ruleId != null) Decision.DisableRules(pkg, listOf(ruleId), "重试风暴")
                // Unspecified in the plan: a storm without a rule cannot be located, so report it.
                else Decision.NoRuleFound(pkg, "重试风暴")
            SignalKind.USER_UNDO -> when {
                ruleId != null -> Decision.DisableRules(pkg, listOf(ruleId), "你撤销了拦截")
                sameKindCount24h >= UNDO_REPEAT_THRESHOLD -> fromHits("你撤销了拦截")
                else -> null
            }
            SignalKind.TEMP_ALLOW -> fromHits("你临时放行了该应用")
            SignalKind.CRASH_DIALOG, SignalKind.DROPBOX_CRASH -> fromHits("应用崩溃")
            SignalKind.COLD_START_LOOP -> fromHits("应用反复重启")
        }
    }

    fun evaluateObservation(cfg: AppConfigEntity, undoOrAllowCount: Int): Decision =
        if (undoOrAllowCount > 0) Decision.ExtendObservation(cfg.pkg) else Decision.EndObservation(cfg.pkg)
}
