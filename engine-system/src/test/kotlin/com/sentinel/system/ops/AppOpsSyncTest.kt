package com.sentinel.system.ops

import com.sentinel.system.fakes.*
import com.sentinel.system.profile.ColorOsProfile
import com.sentinel.system.shell.ShizukuState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.*

class AppOpsSyncTest : MemoryDataTest() {
    private val pkg = "com.example.reader"
    private val backgroundOp = "BACKGROUND_POPUP_TEST"

    // UT-SY-4-01
    @Test fun UT_SY_4_01_overlay_background_and_clipboard_commands_are_logged() = runTest {
        seedApp(pkg, overlay = true, clipboard = true)
        val device = FakeDevice().apply {
            appops[pkg to "SYSTEM_ALERT_WINDOW"] = "allow"
            appops[pkg to backgroundOp] = "allow"
            appops[pkg to "READ_CLIPBOARD"] = "allow"
        }
        val executor = OpExecutor(device, logs, events)
        val sync = appSync(device, executor, ColorOsProfile("TEST", backgroundOp, emptyList()), device.state)
        assertTrue(sync.syncOnce() > 0)
        assertEquals("deny", device.appops[pkg to "SYSTEM_ALERT_WINDOW"])
        assertEquals("ignore", device.appops[pkg to backgroundOp])
        assertEquals("ignore", device.appops[pkg to "READ_CLIPBOARD"])
        val expected = mapOf(
            "appop:SYSTEM_ALERT_WINDOW:$pkg" to "appops set $pkg SYSTEM_ALERT_WINDOW deny",
            "appop:$backgroundOp:$pkg" to "appops set $pkg $backgroundOp ignore",
            "appop:READ_CLIPBOARD:$pkg" to "appops set $pkg READ_CLIPBOARD ignore",
        )
        val entries = logs.observeAll().first()
        assertEquals(expected, entries.associate { it.opId to it.command })
        assertTrue(entries.all { it.success && !it.undone })
        assertTrue(entries.all { it.beforeState.contains("allow") })
        assertTrue(device.commands.containsAll(expected.values))
    }
    // UT-SY-4-02
    @Test fun UT_SY_4_02_turning_off_restores_each_original_mode() = runTest {
        seedApp(pkg, overlay = true, clipboard = true)
        val device = FakeDevice().apply {
            appops[pkg to "SYSTEM_ALERT_WINDOW"] = "allow"
            appops[pkg to backgroundOp] = "foreground"
            appops[pkg to "READ_CLIPBOARD"] = "allow"
        }
        val initial = device.snapshot()
        val sync = appSync(device, OpExecutor(device, logs, events), ColorOsProfile("TEST", backgroundOp, emptyList()), device.state)
        sync.syncOnce()
        configs.setToggles(pkg) { it.copy(limitOverlay = false, denyClipboard = false) }
        sync.syncOnce()
        assertEquals(initial, device.snapshot())
        assertTrue(device.commands.contains("appops set $pkg SYSTEM_ALERT_WINDOW allow"))
        assertTrue(device.commands.contains("appops set $pkg $backgroundOp foreground"))
        assertTrue(device.commands.contains("appops set $pkg READ_CLIPBOARD allow"))
        assertTrue(logs.observeAll().first().filter { it.success }.all { it.undone })
    }
    // UT-SY-4-03
    @Test fun UT_SY_4_03_offline_queues_then_ready_clears_pending() = runTest {
        seedApp(pkg, overlay = true)
        val device = FakeDevice().apply {
            appops[pkg to "SYSTEM_ALERT_WINDOW"] = "allow"
            state.value = ShizukuState.NOT_RUNNING
        }
        val initial = device.snapshot()
        val sync = appSync(device, OpExecutor(device, logs, events), ColorOsProfile("TEST", ops = emptyList()), device.state)
        assertEquals(0, sync.syncOnce())
        assertTrue(sync.pendingCount.value > 0)
        assertEquals(emptyList(), device.commands)
        assertEquals(initial, device.snapshot())
        device.state.value = ShizukuState.READY
        assertTrue(sync.syncOnce() > 0)
        assertEquals(0, sync.pendingCount.value)
        assertEquals("deny", device.appops[pkg to "SYSTEM_ALERT_WINDOW"])
        assertTrue(logs.observeAll().first().single().success)
    }
    // UT-SY-4-04
    @Test fun UT_SY_4_04_missing_background_op_only_changes_overlay() = runTest {
        seedApp(pkg, overlay = true)
        val device = FakeDevice().apply { appops[pkg to "SYSTEM_ALERT_WINDOW"] = "allow" }
        val sync = appSync(device, OpExecutor(device, logs, events), ColorOsProfile("TEST", null, emptyList()), device.state)
        sync.syncOnce()
        assertEquals(listOf("appops set $pkg SYSTEM_ALERT_WINDOW deny"), device.commands.filter { it.startsWith("appops set") })
        assertEquals("deny", device.appops[pkg to "SYSTEM_ALERT_WINDOW"])
        assertEquals("appop:SYSTEM_ALERT_WINDOW:$pkg", logs.observeAll().first().single().opId)
    }
}
