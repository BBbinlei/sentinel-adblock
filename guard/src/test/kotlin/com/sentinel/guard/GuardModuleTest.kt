package com.sentinel.guard

import androidx.work.ListenableWorker
import com.sentinel.data.db.*
import com.sentinel.data.policy.EffectivePolicy
import com.sentinel.data.repo.GlobalStateRepository
import com.sentinel.guard.fakes.GuardFixture
import com.sentinel.rules.model.EventKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class GuardModuleTest : GuardFixture() {
    @Test fun MT_GD_01_degrade_undo_pin_and_retry_closed_loop() = runTest(dispatcher) {
        register()
        runner.start(backgroundScope)
        runCurrent()
        events.log(pkg, EventKind.DNS_BLOCKED, rule, "ads.example.test")
        assertEquals(listOf(rule), events.rulesHitSince(pkg, clock.now() - 600_000))
        apps.tempAllow(pkg)
        runCurrent()
        assertEquals(setOf(rule), disabled())
        assertEquals(listOf(rule), notifier.disabled.single().ids)
        assertEquals(pkg, notifier.disabled.single().pkg)
        assertEquals("你临时放行了该应用", notifier.disabled.single().reason)
        assertHandled()
        runner.undo(pkg, listOf(rule))
        assertEquals(emptySet(), disabled())
        assertEquals("PINNED", overrideState())
        // Finish the temporary allow, so the second signal represents active protection.
        clock.time += 86_400_001L
        assertEquals(ProtectLevel.STANDARD, apps.effective(pkg).level)
        signals.emit(pkg, SignalKind.RETRY_STORM, rule)
        runCurrent()
        assertEquals(emptySet(), disabled())
        assertEquals("PINNED", overrideState())
        assertEquals(1, notifier.disabled.size)
        assertEquals(pkg, notifier.noRule.single().pkg)
        assertHandled(count = 2)
    }
    @Test fun MT_GD_02_new_app_stays_observing_until_worker_evaluates() = runTest(dispatcher) {
        register(fresh = true)
        val cfg = assertNotNull(apps.observe(pkg).first())
        assertEquals(clock.now() + 259_200_000L, cfg.observationEndsAt)
        assertTrue(apps.effective(pkg).observing)
        clock.time += 259_200_000L
        assertTrue(apps.effective(pkg).observing)
        assertEquals(listOf(pkg), apps.observationDue().map { it.pkg })
        assertEquals(ListenableWorker.Result.success(), runObservation())
        val evaluated = assertNotNull(apps.observe(pkg).first())
        assertEquals(0L, evaluated.observationEndsAt)
        val global = koin.get<GlobalStateRepository>().get()
        val effective = EffectivePolicy.resolve(evaluated, pkg, global, clock.now())
        assertFalse(effective.observing)
        assertEquals(ProtectLevel.STANDARD, effective.level)
        assertFalse(apps.effective(pkg).observing)
    }
}
