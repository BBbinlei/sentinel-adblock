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
    private val verifiedAppOps = setOf("SYSTEM_ALERT_WINDOW", "READ_CLIPBOARD", backgroundOp)

    // UT-SY-4-01
    @Test fun UT_SY_4_01_overlay_background_and_clipboard_commands_are_logged() = runTest {
        seedApp(pkg, overlay = true, clipboard = true)
        val device = FakeDevice().apply {
            appops[pkg to "SYSTEM_ALERT_WINDOW"] = "allow"
            appops[pkg to backgroundOp] = "allow"
            appops[pkg to "READ_CLIPBOARD"] = "allow"
        }
        val executor = OpExecutor(device, logs, events)
        val sync = appSync(device, executor, ColorOsProfile("TEST", backgroundOp, emptyList(), verifiedAppOps), device.state)
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
        val sync = appSync(device, OpExecutor(device, logs, events), ColorOsProfile("TEST", backgroundOp, emptyList(), verifiedAppOps), device.state)
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
        val sync = appSync(device, OpExecutor(device, logs, events), ColorOsProfile("TEST", ops = emptyList(), verifiedAppOps = verifiedAppOps), device.state)
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
        val sync = appSync(device, OpExecutor(device, logs, events), ColorOsProfile("TEST", null, emptyList(), verifiedAppOps), device.state)
        sync.syncOnce()
        assertEquals(listOf("appops set $pkg SYSTEM_ALERT_WINDOW deny"), device.commands.filter { it.startsWith("appops set") })
        assertEquals("deny", device.appops[pkg to "SYSTEM_ALERT_WINDOW"])
        assertEquals("appop:SYSTEM_ALERT_WINDOW:$pkg", logs.observeAll().first().single().opId)
    }

    @Test fun UT_SY_4_05_null_profile_keeps_pending_without_commands() = runTest {
        assertUnverifiedPending(null, expectedPending = 2)
    }

    @Test fun UT_SY_4_06_empty_verified_appops_keeps_pending_without_commands() = runTest {
        assertUnverifiedPending(ColorOsProfile("TEST", backgroundOp, emptyList()), expectedPending = 3)
    }

    private suspend fun assertUnverifiedPending(profile: ColorOsProfile?, expectedPending: Int) {
        seedApp(pkg, overlay = true, clipboard = true)
        val device = FakeDevice().apply {
            verifiedAppOps.forEach { appops[pkg to it] = "allow" }
        }
        val initial = device.snapshot()
        val sync = appSync(device, OpExecutor(device, logs, events, clock), profile, device.state)
        for (state in listOf(ShizukuState.READY, ShizukuState.NOT_RUNNING, ShizukuState.READY)) {
            device.state.value = state
            assertEquals(0, sync.syncOnce())
            assertEquals(expectedPending, sync.pendingCount.value)
            assertEquals(emptyList(), device.commands)
            assertEquals(initial, device.snapshot())
            assertEquals(emptyList(), logs.observeAll().first())
        }
        val config = configs.observeAll().first().single()
        assertTrue(config.limitOverlay)
        assertTrue(config.denyClipboard)
    }

    @Test fun UT_SY_4_07_partial_verified_appops_only_executes_declared_clipboard() = runTest {
        seedApp(pkg, overlay = true, clipboard = true)
        val device = FakeDevice().apply {
            verifiedAppOps.forEach { appops[pkg to it] = "allow" }
        }
        val profile = ColorOsProfile("TEST", backgroundOp, emptyList(), setOf("READ_CLIPBOARD"))
        val sync = appSync(device, OpExecutor(device, logs, events, clock), profile, device.state)
        assertEquals(1, sync.syncOnce())
        assertEquals(2, sync.pendingCount.value)
        assertEquals(listOf("appops get $pkg READ_CLIPBOARD", "appops set $pkg READ_CLIPBOARD ignore",
            "appops get $pkg READ_CLIPBOARD"), device.commands)
        assertEquals("allow", device.appops[pkg to "SYSTEM_ALERT_WINDOW"])
        assertEquals("allow", device.appops[pkg to backgroundOp])
        assertEquals("ignore", device.appops[pkg to "READ_CLIPBOARD"])
        assertEquals("appop:READ_CLIPBOARD:$pkg", logs.observeAll().first().single().opId)
        device.commands.clear()
        assertEquals(0, sync.syncOnce())
        assertEquals(2, sync.pendingCount.value)
        assertEquals(listOf("appops get $pkg READ_CLIPBOARD"), device.commands)
    }

    @Test fun UT_SY_4_08_unverified_profiles_do_not_probe_or_restore_historical_appops() = runTest {
        seedApp(pkg, overlay = true, clipboard = true)
        val device = FakeDevice().apply {
            verifiedAppOps.forEach { appops[pkg to it] = "allow" }
        }
        val executor = OpExecutor(device, logs, events, clock)
        val verified = ColorOsProfile("TEST", backgroundOp, emptyList(), verifiedAppOps)
        assertEquals(3, appSync(device, executor, verified, device.state).syncOnce())
        val applied = device.snapshot()
        val activeLogs = logs.observeAll().first()
        device.commands.clear()
        for (profile in listOf(null, verified.copy(verifiedAppOps = emptySet()))) {
            configs.setToggles(pkg) { it.copy(limitOverlay = true, denyClipboard = true) }
            val sync = appSync(device, executor, profile, device.state)
            assertEquals(0, sync.syncOnce())
            assertEquals(3, sync.pendingCount.value)
            configs.setToggles(pkg) { it.copy(limitOverlay = false, denyClipboard = false) }
            assertEquals(0, sync.syncOnce())
            assertEquals(3, sync.pendingCount.value)
            assertEquals(emptyList(), device.commands)
            assertEquals(applied, device.snapshot())
            assertEquals(activeLogs, logs.observeAll().first())
        }
    }
}
