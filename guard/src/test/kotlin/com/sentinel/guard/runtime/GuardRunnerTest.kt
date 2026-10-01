package com.sentinel.guard.runtime

import com.sentinel.data.db.SignalKind
import com.sentinel.guard.fakes.GuardFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class GuardRunnerTest : GuardFixture() {
    @Test fun UT_GD_2_01_retry_storm_disables_and_marks_handled() = runTest(dispatcher) {
        register()
        runner.start(backgroundScope)
        runCurrent()
        signals.emit(pkg, SignalKind.RETRY_STORM, rule)
        runCurrent()
        assertEquals(setOf(rule), disabled())
        assertHandled()
        assertEquals(listOf(rule), notifier.disabled.single().ids)
        assertEquals(pkg, notifier.disabled.single().pkg)
        assertEquals(label, notifier.disabled.single().label)
        assertEquals("重试风暴", notifier.disabled.single().reason)
    }
    @Test fun UT_GD_2_02_pinned_rule_produces_no_rule_notice() = runTest(dispatcher) {
        register()
        overrides.pin(pkg, rule)
        runner.start(backgroundScope)
        runCurrent()
        signals.emit(pkg, SignalKind.RETRY_STORM, rule)
        runCurrent()
        assertEquals(emptySet(), disabled())
        assertEquals("PINNED", overrideState())
        assertEquals(emptyList(), notifier.disabled)
        assertEquals(pkg, notifier.noRule.single().pkg)
        assertEquals(label, notifier.noRule.single().label)
        assertHandled()
    }
    @Test fun UT_GD_2_03_one_notification_failure_does_not_stop_collection() = runTest(dispatcher) {
        register()
        notifier.failNextDisabled = true
        runner.start(backgroundScope)
        runCurrent()
        signals.emit(pkg, SignalKind.RETRY_STORM, "R1")
        runCurrent()
        assertHandled()
        signals.emit(pkg, SignalKind.RETRY_STORM, "R2")
        runCurrent()
        assertEquals(setOf("R1", "R2"), disabled())
        assertHandled(count = 2)
        assertEquals(listOf("R2"), notifier.disabled.single().ids)
    }
    @Test fun UT_GD_2_04_undo_removes_disabled_state_and_pins_rules() = runTest(dispatcher) {
        register()
        assertTrue(overrides.disable(pkg, rule, "自动降级"))
        assertTrue(overrides.disable(pkg, "R2", "自动降级"))
        assertTrue(overrides.disable("com.example.other", rule, "其他应用"))
        runner.undo(pkg, listOf(rule, "R2"))
        assertEquals(emptySet(), disabled())
        assertEquals("PINNED", overrideState())
        assertEquals("PINNED", overrideState(ruleId = "R2"))
        assertFalse(overrides.disable(pkg, rule, "再次误伤"))
        assertEquals(setOf(rule), disabled("com.example.other"))
    }
}
