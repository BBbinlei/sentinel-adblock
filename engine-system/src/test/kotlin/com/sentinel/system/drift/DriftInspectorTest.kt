package com.sentinel.system.drift

import com.sentinel.system.fakes.*
import com.sentinel.system.ops.*
import com.sentinel.system.profile.ColorOsProfile
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.*

class DriftInspectorTest : MemoryDataTest() {
    // UT-SY-5-01
    @Test fun UT_SY_5_01_probe_mismatch_is_reported_as_drift() = runTest {
        val device = FakeDevice()
        val ops = listOf(settingOp("drifted"), settingOp("stable"))
        ops.forEach { device.settings["secure" to it.id] = "1" }
        val executor = OpExecutor(device, logs, events)
        assertTrue(executor.applyAll(ops).values.all { it == OpOutcome.Applied })
        device.settings["secure" to "drifted"] = "1"
        val report = assertNotNull(inspector(executor, ColorOsProfile("TEST", ops = ops), device.state).inspect())
        assertEquals(2, report.checked)
        assertEquals(listOf("drifted"), report.drifted)
    }
    // UT-SY-5-02
    @Test fun UT_SY_5_02_undone_operations_are_not_probed() = runTest {
        val device = FakeDevice()
        val ops = listOf(settingOp("undone"), settingOp("active"))
        ops.forEach { device.settings["secure" to it.id] = "1" }
        val executor = OpExecutor(device, logs, events)
        executor.applyAll(ops)
        assertTrue(executor.undo(requireNotNull(logs.latestApplied("undone")).id))
        val start = device.commands.size
        val report = assertNotNull(inspector(executor, ColorOsProfile("TEST", ops = ops), device.state).inspect())
        assertEquals(1, report.checked)
        assertEquals(emptyList(), report.drifted)
        assertEquals(listOf("settings get secure active"), device.commands.drop(start))
    }
    // UT-SY-5-04
    @Test fun UT_SY_5_04_reapply_only_selected_ids() = runTest {
        val device = FakeDevice()
        val ops = listOf(settingOp("selected"), settingOp("other"))
        ops.forEach { device.settings["secure" to it.id] = "1" }
        val executor = OpExecutor(device, logs, events)
        executor.applyAll(ops)
        ops.forEach { device.settings["secure" to it.id] = "1" }
        val start = device.commands.size
        val result = inspector(executor, ColorOsProfile("TEST", ops = ops), device.state).reapply(listOf("selected"))
        assertEquals(mapOf("selected" to OpOutcome.Applied), result)
        assertEquals("0", device.settings["secure" to "selected"])
        assertEquals("1", device.settings["secure" to "other"])
        assertEquals(listOf("settings get secure selected", "settings put secure selected 0", "settings get secure selected"), device.commands.drop(start))
    }
}
