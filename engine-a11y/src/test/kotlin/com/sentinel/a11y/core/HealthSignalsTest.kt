package com.sentinel.a11y.core

import com.sentinel.a11y.fakes.*
import com.sentinel.data.db.SignalKind
import kotlin.test.*
import org.junit.Test

class HealthSignalsTest {
    @Test fun UT_AY_4_01_chinese_crash_dialog_resolves_app_label() {
        for (suffix in listOf("已停止运行", "屡次停止运行", "没有响应", "已停止")) {
            val clock = TestClock()
            val labels = mutableListOf<String>()
            val health = HealthSignals(clock::read) { label ->
                labels += label
                if (label == "阅读器") APP else null
            }
            val signal = assertNotNull(health.onWindowText("android", listOf("阅读器$suffix", "关闭应用")))
            assertEquals(APP, signal.pkg)
            assertEquals(SignalKind.CRASH_DIALOG, signal.kind)
            assertEquals(listOf("阅读器"), labels)
            assertNull(health.onWindowText("android", listOf("阅读器$suffix")))
            clock.now += 600_000
            assertNotNull(health.onWindowText("android", listOf("阅读器$suffix")))
        }
        val health = HealthSignals({ 1_000L }) { APP }
        assertNull(health.onWindowText(APP, listOf("阅读器已停止运行")))
    }

    @Test fun UT_AY_4_02_unknown_app_label_is_not_a_signal() {
        val health = HealthSignals({ 1_000L }) { null }
        assertNull(health.onWindowText("android", listOf("未知应用已停止运行")))
        assertNull(health.onWindowText("android", listOf("电量不足", "确定")))
    }

    @Test fun UT_AY_4_03_third_launch_signals_once_per_ten_minutes_per_pkg_kind() {
        val clock = TestClock()
        val health = HealthSignals(clock::read) { if (it == "阅读器") APP else null }
        assertNull(health.onLaunch(APP, 1))
        assertNull(health.onLaunch(APP, 2))
        val signal = assertNotNull(health.onLaunch(APP, 3))
        assertEquals(APP, signal.pkg)
        assertEquals(SignalKind.COLD_START_LOOP, signal.kind)
        assertNull(health.onLaunch(APP, 4))
        assertNotNull(health.onLaunch("com.example.other", 3))
        assertEquals(SignalKind.CRASH_DIALOG,
            assertNotNull(health.onWindowText("android", listOf("阅读器已停止运行"))).kind)
        clock.now += 599_999
        assertNull(health.onLaunch(APP, 3))
        clock.now += 1
        assertEquals(SignalKind.COLD_START_LOOP, assertNotNull(health.onLaunch(APP, 3)).kind)
    }
}
