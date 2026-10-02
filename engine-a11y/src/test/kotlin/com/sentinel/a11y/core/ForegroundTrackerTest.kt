package com.sentinel.a11y.core

import com.sentinel.a11y.fakes.*
import kotlin.test.*
import org.junit.Test

class ForegroundTrackerTest {
    @Test fun UT_AY_1_01_launcher_origin() {
        val tracker = tracker()
        tracker.onInput(window(HOME, 100))
        tracker.onInput(window(APP, 1_000, ACTIVITY))
        assertEquals(LaunchInfo(APP, 1_000, true), tracker.launch)
        assertEquals(APP, tracker.currentPkg)
        assertEquals(ACTIVITY, tracker.currentActivity)
        assertEquals(1_000L, tracker.lastWindowAt(APP))
    }

    @Test fun UT_AY_1_02_systemui_is_not_launcher() {
        val tracker = tracker()
        assertNull(tracker.onInput(window(SYSTEM_UI, 100)))
        tracker.onInput(window(APP, 1_000))
        assertFalse(assertNotNull(tracker.launch).fromLauncher)
        assertEquals(APP, tracker.launch?.pkg)
    }

    @Test fun UT_AY_1_03_ime_does_not_change_foreground() {
        val tracker = tracker()
        tracker.onInput(window(APP, 1_000, ACTIVITY))
        val launch = tracker.launch
        assertNull(tracker.onInput(window(IME, 1_500)))
        assertEquals(APP, tracker.currentPkg)
        assertNull(tracker.onInput(window(APP, 2_000, ACTIVITY)))
        assertEquals(launch, tracker.launch)
        assertEquals(2_000L, tracker.lastWindowAt(APP))
    }

    @Test fun UT_AY_1_04_clicked_is_also_interaction() {
        val tracker = tracker()
        tracker.onInput(window(APP, 1_000))
        assertNull(tracker.lastInteractionAt(APP))
        assertNull(tracker.onInput(clicked(ts = 1_200)))
        assertEquals(1_200L, tracker.lastInteractionAt(APP))
        tracker.onInput(UiInput.Interaction(APP, 1_300))
        assertEquals(1_300L, tracker.lastInteractionAt(APP))
        assertNull(tracker.lastInteractionAt(TAOBAO))
    }

    @Test fun UT_AY_1_05_launch_count_uses_pkg_and_time_window() {
        val tracker = tracker()
        tracker.onInput(window(APP, 1_000))
        tracker.onInput(window("com.example.other", 2_000))
        tracker.onInput(window(APP, 30_000))
        tracker.onInput(window("com.example.other", 31_000))
        tracker.onInput(window(APP, 60_000))
        tracker.onInput(window(APP, 60_001, null)) // 同包弹窗不计作一次启动。
        assertEquals(3, tracker.launchesWithin(APP, 60_000, 60_001))
        assertEquals(2, tracker.launchesWithin(APP, 60_000, 61_001))
        assertEquals(2, tracker.launchesWithin("com.example.other", 60_000, 61_001))
        assertEquals(0, tracker.launchesWithin("com.example.unknown", 60_000, 61_001))
        assertEquals(1, tracker.launchesWithin(APP, 60_000, 90_001))
    }
}
