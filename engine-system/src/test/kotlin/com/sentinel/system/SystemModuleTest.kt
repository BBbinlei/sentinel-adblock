package com.sentinel.system

import com.sentinel.system.fakes.*
import com.sentinel.system.ops.*
import com.sentinel.system.profile.*
import com.sentinel.system.shell.ShizukuState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.*

class SystemModuleTest : MemoryDataTest() {
    private val pkg = "com.example.reader"
    private val ads = "com.example.ads"
    private val quickapp = "com.example.quickapp"

    private fun device() = FakeDevice().apply {
        settings["secure" to "recommend"] = "1"
        settings["global" to "tracking"] = "0"
        settings["secure" to "not-verified"] = "1"
        enabledPackages[quickapp] = true
        installedPackages[quickapp] = true
        enabledPackages[ads] = true
        installedPackages[ads] = true
        appops[pkg to "READ_CLIPBOARD"] = "allow"
        appops[pkg to "SYSTEM_ALERT_WINDOW"] = "allow"
    }
    private fun profile() = ColorOsProfile("TEST", ops = listOf(
        settingOp(),
        ProfileOp("tracking", 10, "限制追踪", OpKind.SETTING, true,
            "settings put global tracking 1", "settings put global tracking {before}",
            "settings get global tracking", "^1$"),
        ProfileOp("quickapp", 8, "禁用测试快应用", OpKind.DISABLE, true,
            "pm disable-user --user 0 $quickapp", "pm enable --user 0 $quickapp",
            "pm list packages -d $quickapp", "^package:com\\.example\\.quickapp$"),
        ProfileOp("ads", 4, "卸载测试广告包", OpKind.UNINSTALL, true,
            "pm uninstall -k --user 0 $ads", "pm install-existing --user 0 $ads",
            "pm list packages $ads", "^$", optional = true),
        settingOp("not-verified", verified = false),
        ProfileOp("manual", 2, "设置引导", OpKind.WIZARD, true, intent = SettingsIntent(action = "android.settings.SETTINGS")),
    ), verifiedAppOps = setOf("SYSTEM_ALERT_WINDOW", "READ_CLIPBOARD"))

    // MT-SY-01
    @Test fun MT_SY_01_purify_then_undo_restores_complete_device_snapshot() = runTest {
        seedApp(pkg, overlay = true, clipboard = true)
        val device = device()
        val initial = device.snapshot()
        val profile = profile()
        val executor = OpExecutor(device, logs, events)
        val sync = appSync(device, executor, profile, device.state)
        val result = executor.applyAll(profile.ops)
        assertEquals(profile.ops.map { it.id }.toSet(), result.keys)
        assertEquals(4, result.values.count { it == OpOutcome.Applied })
        assertEquals(OpOutcome.NotVerified, result["not-verified"])
        assertEquals(OpOutcome.WizardOnly, result["manual"])
        assertTrue(sync.syncOnce() > 0)
        assertEquals("0", device.settings["secure" to "recommend"])
        assertEquals("1", device.settings["global" to "tracking"])
        assertEquals(false, device.enabledPackages[quickapp])
        assertEquals(false, device.installedPackages[ads])
        assertEquals("deny", device.appops[pkg to "SYSTEM_ALERT_WINDOW"])
        assertEquals("ignore", device.appops[pkg to "READ_CLIPBOARD"])
        assertEquals("1", device.settings["secure" to "not-verified"])
        assertFalse(device.commands.any { "not-verified" in it })
        assertEquals(6, logs.observeAll().first().count { it.success && !it.undone })
        assertEquals(emptyList(), assertNotNull(inspector(executor, profile, device.state).inspect()).drifted)
        // 停止后续同步意图，undoAll 针对已记录操作还原设备。
        configs.setToggles(pkg) { it.copy(limitOverlay = false, denyClipboard = false) }
        assertEquals(6, executor.undoAll())
        assertEquals(initial, device.snapshot())
        assertTrue(logs.observeAll().first().all { it.success && it.undone })
        val end = device.commands.size
        assertEquals(0, executor.undoAll())
        assertEquals(end, device.commands.size)
    }
    // MT-SY-02
    @Test fun MT_SY_02_upgrade_drift_is_found_and_reapplied() = runTest {
        seedApp(pkg)
        val device = device()
        val profile = profile()
        val executor = OpExecutor(device, logs, events)
        executor.applyAll(profile.ops)
        appSync(device, executor, profile, device.state).syncOnce()
        val clean = device.snapshot()
        device.settings["secure" to "recommend"] = "1"
        device.settings["global" to "tracking"] = "0"
        val inspector = inspector(executor, profile, device.state)
        val report = assertNotNull(inspector.inspect())
        assertEquals(4, report.checked)
        assertEquals(setOf("recommend", "tracking"), report.drifted.toSet())
        val result = inspector.reapply(report.drifted)
        assertEquals(setOf("recommend", "tracking"), result.keys)
        assertTrue(result.values.all { it == OpOutcome.Applied })
        assertEquals(clean, device.snapshot())
        assertEquals(emptyList(), assertNotNull(inspector.inspect()).drifted)
    }
    // MT-SY-03
    @Test fun MT_SY_03_disconnect_preserves_applied_and_reconnect_runs_pending() = runTest {
        seedApp(pkg)
        val device = device()
        val profile = profile()
        val executor = OpExecutor(device, logs, events)
        assertEquals(OpOutcome.Applied, executor.apply(profile.ops.first()))
        val applied = device.snapshot()
        val sync = appSync(device, executor, profile, device.state)
        configs.setToggles(pkg) { it.copy(limitOverlay = true, denyClipboard = true) }
        device.state.value = ShizukuState.NOT_RUNNING
        val start = device.commands.size
        assertEquals(0, sync.syncOnce())
        assertTrue(sync.pendingCount.value > 0)
        assertNull(inspector(executor, profile, device.state).inspect())
        assertEquals(start, device.commands.size)
        assertEquals(applied, device.snapshot())
        assertEquals(1, logs.observeAll().first().size)
        device.state.value = ShizukuState.READY
        assertTrue(sync.syncOnce() > 0)
        assertEquals(0, sync.pendingCount.value)
        assertEquals("0", device.settings["secure" to "recommend"])
        assertEquals("deny", device.appops[pkg to "SYSTEM_ALERT_WINDOW"])
        assertEquals("ignore", device.appops[pkg to "READ_CLIPBOARD"])
        assertEquals(3, logs.observeAll().first().count { it.success })
        assertFalse(device.commands.drop(start).any { it.startsWith("settings put") })
    }
}
