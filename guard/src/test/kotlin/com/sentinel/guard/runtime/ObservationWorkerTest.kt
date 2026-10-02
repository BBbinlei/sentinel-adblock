package com.sentinel.guard.runtime

import androidx.work.ListenableWorker
import com.sentinel.data.db.SignalKind
import com.sentinel.guard.fakes.GuardFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class ObservationWorkerTest : GuardFixture() {
    @Test fun UT_GD_2_05_temp_allow_during_observation_extends_three_days() = runTest(dispatcher) {
        register(fresh = true)
        val firstSeen = assertNotNull(apps.observe(pkg).first()).firstSeenAt
        clock.time += 86_400_000L
        apps.tempAllow(pkg)
        assertEquals(1, signals.countSince(pkg, setOf(SignalKind.TEMP_ALLOW), firstSeen))
        clock.time = firstSeen + 259_200_000L
        assertEquals(ListenableWorker.Result.success(), runObservation())
        val cfg = assertNotNull(apps.observe(pkg).first())
        assertEquals(clock.now() + 259_200_000L, cfg.observationEndsAt)
        assertTrue(apps.effective(pkg).observing)
        assertEquals(listOf(pkg to label), notifier.extended)
    }
    @Test fun UT_GD_2_06_clean_observation_ends_without_extension_notice() = runTest(dispatcher) {
        register(fresh = true)
        // Crash signals do not count as USER_UNDO or TEMP_ALLOW.
        signals.emit(pkg, SignalKind.CRASH_DIALOG)
        clock.time = assertNotNull(apps.observe(pkg).first()).observationEndsAt
        assertEquals(ListenableWorker.Result.success(), runObservation())
        assertEquals(0L, assertNotNull(apps.observe(pkg).first()).observationEndsAt)
        assertFalse(apps.effective(pkg).observing)
        assertEquals(emptyList(), notifier.extended)
        assertEquals(emptyList(), apps.observationDue())
    }

    private suspend fun singleSignalExtendsOnce(kind: SignalKind) {
        register(fresh = true)
        val initial = assertNotNull(apps.observe(pkg).first())
        assertEquals(initial.firstSeenAt, initial.observationEndsAt - ObservationWorker.EXTEND_MS)
        clock.time = initial.firstSeenAt + 86_400_000L
        signals.emit(pkg, kind)
        val signal = signals.observeUnhandled().first().single()
        signals.markHandled(listOf(signal.id)) // Consumed signals still belong to this observation.
        clock.time = initial.observationEndsAt
        assertEquals(ListenableWorker.Result.success(), runObservation())
        val extended = assertNotNull(apps.observe(pkg).first()).observationEndsAt
        assertEquals(clock.now() + ObservationWorker.EXTEND_MS, extended)
        assertEquals(1, signals.countSince(pkg, setOf(kind), initial.firstSeenAt))
        assertEquals(0, signals.countSince(pkg, setOf(kind), extended - ObservationWorker.EXTEND_MS))
        assertEquals(listOf(pkg to label), notifier.extended)
        clock.time = extended - 1
        assertEquals(ListenableWorker.Result.success(), runObservation())
        assertEquals(extended, assertNotNull(apps.observe(pkg).first()).observationEndsAt)
        clock.time = extended
        assertEquals(ListenableWorker.Result.success(), runObservation())
        assertEquals(0L, assertNotNull(apps.observe(pkg).first()).observationEndsAt)
        assertFalse(apps.effective(pkg).observing)
        assertEquals(listOf(pkg to label), notifier.extended)
        assertEquals(1, signals.countSince(pkg, setOf(kind), initial.firstSeenAt))
    }

    @Test fun R14_01_single_USER_UNDO_is_not_recounted_after_extension() = runTest(dispatcher) {
        singleSignalExtendsOnce(SignalKind.USER_UNDO)
    }
    @Test fun R14_02_single_TEMP_ALLOW_is_not_recounted_after_extension() = runTest(dispatcher) {
        singleSignalExtendsOnce(SignalKind.TEMP_ALLOW)
    }

    @Test fun R14_03_first_round_includes_firstSeenAt_but_excludes_previous_millisecond() = runTest(dispatcher) {
        for (offset in listOf(-1L, 0L)) {
            val app = "$pkg.boundary${if (offset < 0) "before" else "at"}"
            register(app, fresh = true)
            val cfg = assertNotNull(apps.observe(app).first())
            assertEquals(cfg.firstSeenAt, cfg.observationEndsAt - ObservationWorker.EXTEND_MS)
            clock.time = cfg.firstSeenAt + offset
            signals.emit(app, SignalKind.USER_UNDO)
            assertEquals(1, signals.countSince(app, setOf(SignalKind.USER_UNDO), 0))
            clock.time = cfg.observationEndsAt
            assertEquals(ListenableWorker.Result.success(), runObservation())
            assertEquals(if (offset == 0L) clock.now() + ObservationWorker.EXTEND_MS else 0L,
                assertNotNull(apps.observe(app).first()).observationEndsAt)
        }
        assertEquals(listOf("$pkg.boundaryat" to label), notifier.extended)
    }

    @Test fun R14_04_delayed_worker_uses_actual_extension_start_and_new_signal_boundary() = runTest(dispatcher) {
        for (offset in listOf(-1L, 0L)) {
            val app = "$pkg.delayed${if (offset < 0) "before" else "at"}"
            register(app, fresh = true)
            val initial = assertNotNull(apps.observe(app).first())
            signals.emit(app, SignalKind.USER_UNDO)
            clock.time = initial.observationEndsAt + 3_600_000L
            assertEquals(ListenableWorker.Result.success(), runObservation())
            val extended = assertNotNull(apps.observe(app).first()).observationEndsAt
            val newStart = clock.now()
            assertEquals(newStart + ObservationWorker.EXTEND_MS, extended)
            clock.time = newStart + offset
            signals.emit(app, SignalKind.TEMP_ALLOW)
            clock.time = extended
            assertEquals(ListenableWorker.Result.success(), runObservation())
            assertEquals(if (offset == 0L) extended + ObservationWorker.EXTEND_MS else 0L,
                assertNotNull(apps.observe(app).first()).observationEndsAt)
        }
        assertEquals(3, notifier.extended.size)
    }

    @Test fun R14_05_other_signal_kinds_do_not_extend_observation() = runTest(dispatcher) {
        register(fresh = true)
        for (kind in SignalKind.entries - setOf(SignalKind.USER_UNDO, SignalKind.TEMP_ALLOW)) {
            signals.emit(pkg, kind, rule)
        }
        assertEquals(SignalKind.entries.size - 2, signals.countSince(pkg, SignalKind.entries.toSet(), 0))
        clock.time = assertNotNull(apps.observe(pkg).first()).observationEndsAt
        assertEquals(ListenableWorker.Result.success(), runObservation())
        assertEquals(0L, assertNotNull(apps.observe(pkg).first()).observationEndsAt)
        assertTrue(notifier.extended.isEmpty())
    }
}
