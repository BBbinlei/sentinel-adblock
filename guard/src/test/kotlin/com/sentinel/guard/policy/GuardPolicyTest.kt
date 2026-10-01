package com.sentinel.guard.policy

import com.sentinel.data.db.*
import org.junit.Test
import kotlin.test.*

class GuardPolicyTest {
    private val pkg = "com.example.reader"
    private fun signal(kind: SignalKind, ruleId: String? = null) =
        SignalEntity(id = 7, ts = 1_800_000_000_000L, pkg = pkg, kind = kind, ruleId = ruleId)
    private fun cfg() = AppConfigEntity(pkg = pkg, label = "阅读器", level = null,
        sensitive = false, firstSeenAt = 1, observationEndsAt = 259_200_001)

    @Test fun UT_GD_1_01_retry_storm_disables_explicit_rule() {
        assertEquals(Decision.DisableRules(pkg, listOf("R"), "重试风暴"),
            GuardPolicy.onSignal(signal(SignalKind.RETRY_STORM, "R"), listOf("unrelated"), 1))
    }
    @Test fun UT_GD_1_02_undo_with_rule_does_not_require_second_signal() {
        assertEquals(Decision.DisableRules(pkg, listOf("R"), "你撤销了拦截"),
            GuardPolicy.onSignal(signal(SignalKind.USER_UNDO, "R"), listOf("unrelated"), 1))
    }
    @Test fun UT_GD_1_03_repeated_undo_disables_recent_hits() {
        for (count in listOf(2, 3)) assertEquals(
            Decision.DisableRules(pkg, listOf("R1", "R2"), "你撤销了拦截"),
            GuardPolicy.onSignal(signal(SignalKind.USER_UNDO), listOf("R1", "R2"), count))
    }
    @Test fun UT_GD_1_04_repeated_undo_without_hits_reports_no_rule() {
        assertEquals(Decision.NoRuleFound(pkg, "你撤销了拦截"),
            GuardPolicy.onSignal(signal(SignalKind.USER_UNDO), emptyList(), 2))
    }
    @Test fun UT_GD_1_05_first_undo_without_rule_is_ignored() {
        for (count in 0..1) assertNull(
            GuardPolicy.onSignal(signal(SignalKind.USER_UNDO), listOf("R"), count))
    }
    @Test fun UT_GD_1_06_temp_allow_disables_recent_hits() {
        assertEquals(Decision.DisableRules(pkg, listOf("R"), "你临时放行了该应用"),
            GuardPolicy.onSignal(signal(SignalKind.TEMP_ALLOW), listOf("R"), 1))
    }
    @Test fun UT_GD_1_07_all_crash_kinds_without_hits_report_no_rule() {
        for ((kind, reason) in listOf(SignalKind.CRASH_DIALOG to "应用崩溃",
            SignalKind.COLD_START_LOOP to "应用反复重启", SignalKind.DROPBOX_CRASH to "应用崩溃")) {
            assertEquals(Decision.NoRuleFound(pkg, reason),
                GuardPolicy.onSignal(signal(kind), emptyList(), 1), kind.name)
        }
    }
    @Test fun UT_GD_1_08_hit_window_covers_every_signal_kind() {
        val crashes = setOf(SignalKind.CRASH_DIALOG, SignalKind.COLD_START_LOOP, SignalKind.DROPBOX_CRASH)
        for (kind in SignalKind.entries) assertEquals(if (kind in crashes) 120_000L else 600_000L,
            GuardPolicy.hitWindowMs(kind), kind.name)
    }
    @Test fun UT_GD_1_09_recent_hits_are_distinct() {
        val decision = assertIs<Decision.DisableRules>(GuardPolicy.onSignal(
            signal(SignalKind.TEMP_ALLOW), listOf("R1", "R2", "R1", "R2"), 1))
        assertEquals(pkg, decision.pkg)
        assertEquals(setOf("R1", "R2"), decision.ruleIds.toSet())
        assertEquals(2, decision.ruleIds.size)
        assertEquals("你临时放行了该应用", decision.reason)
    }
    @Test fun UT_GD_1_10_observation_extends_only_with_undo_or_allow() {
        assertEquals(Decision.EndObservation(pkg), GuardPolicy.evaluateObservation(cfg(), 0))
        for (count in listOf(1, 2)) assertEquals(Decision.ExtendObservation(pkg),
            GuardPolicy.evaluateObservation(cfg(), count))
    }
}
