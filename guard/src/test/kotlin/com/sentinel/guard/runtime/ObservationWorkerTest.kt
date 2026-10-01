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
}
