package com.sentinel.a11y.core

import com.sentinel.a11y.fakes.*
import org.junit.Test
import kotlin.test.*

class ForegroundTrackerRegressionTest {
    @Test fun R10_01_home_breaks_transition_and_records_B_launch() {
        val clock = TestClock()
        val tracker = tracker()
        tracker.onInput(window(HOME, clock.now))
        clock.now += 100
        tracker.onInput(window(APP, clock.now))
        val firstLaunch = assertNotNull(tracker.launch)
        clock.now += 1_000
        assertNull(tracker.onInput(window(HOME, clock.now)))
        clock.now += 1_000
        assertNull(tracker.onInput(window(TAOBAO, clock.now)), "A → Home → B is an independent launch")
        assertNull(tracker.lastInteractionAt(APP))
        assertTrue(clock.now - firstLaunch.startedAt < 5_000)
        assertEquals(LaunchInfo(TAOBAO, clock.now, true), tracker.launch)
        assertEquals(TAOBAO, tracker.currentPkg)
        assertEquals(1, tracker.launchesWithin(TAOBAO, 5_000, clock.now))
    }

    @Test fun R10_02_direct_A_to_B_still_produces_transition_and_revert() {
        val clock = TestClock()
        val tracker = tracker()
        tracker.onInput(window(HOME, clock.now))
        clock.now += 100
        tracker.onInput(window(APP, clock.now))
        val before = tracker.copy()
        clock.now += 1_000
        val transition = assertNotNull(tracker.onInput(window(TAOBAO, clock.now)))
        assertEquals(Transition(APP, TAOBAO, clock.now), transition)
        assertEquals(RevertJump(APP, TAOBAO, "launch"), JumpBackGuard(clock::read).onTransition(
            transition, before, { config(it) }, { emptySet() }, { _, _ -> false }))
    }

    @Test fun R10_03_home_relaunches_A_and_copy_preserves_interruption() {
        val tracker = tracker()
        tracker.onInput(window(HOME, 1_000))
        tracker.onInput(window(APP, 1_100))
        tracker.onInput(window(HOME, 2_000))
        val copy = tracker.copy()
        assertNull(copy.onInput(window(TAOBAO, 2_100)))
        assertNull(tracker.onInput(window(APP, 2_200)))
        assertEquals(LaunchInfo(APP, 2_200, true), tracker.launch)
        assertEquals(2, tracker.launchesWithin(APP, 5_000, 2_200))
    }
}
