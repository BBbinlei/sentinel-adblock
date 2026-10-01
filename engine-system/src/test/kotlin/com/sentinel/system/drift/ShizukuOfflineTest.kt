package com.sentinel.system.drift

import com.sentinel.data.db.EngineId
import com.sentinel.data.db.EngineState
import com.sentinel.system.fakes.*
import com.sentinel.system.ops.*
import com.sentinel.system.profile.ColorOsProfile
import com.sentinel.system.shell.ShizukuState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ShizukuOfflineTest : MemoryDataTest() {
    // UT-SY-5-03
    @Test fun UT_SY_5_03_offline_skips_inspection_and_reports_degraded() = runTest {
        val device = FakeDevice()
        val op = settingOp()
        device.settings["secure" to "recommend"] = "1"
        val executor = OpExecutor(device, logs, events)
        assertEquals(OpOutcome.Applied, executor.apply(op))
        val applied = device.snapshot()
        device.state.value = ShizukuState.NOT_RUNNING
        val start = device.commands.size
        val inspector = inspector(executor, ColorOsProfile("TEST", ops = listOf(op)), device.state)
        assertNull(inspector.inspect())
        assertEquals(start, device.commands.size)
        assertEquals(applied, device.snapshot())

        val drifted = MutableStateFlow(0)
        val pending = MutableStateFlow(0)
        SystemStatusReporter(statuses, device.state, drifted, pending).start(backgroundScope)
        runCurrent()
        val offline = statuses.observeAll().first { it[EngineId.SYSTEM]?.state == EngineState.DEGRADED }.getValue(EngineId.SYSTEM)
        assertEquals("Shizuku 未激活（不影响已生效项）", offline.message)
        assertEquals(start, device.commands.size)

        device.state.value = ShizukuState.READY
        runCurrent()
        assertEquals(EngineState.RUNNING, statuses.observeAll().first { it[EngineId.SYSTEM]?.state == EngineState.RUNNING }.getValue(EngineId.SYSTEM).state)
        pending.value = 2
        runCurrent()
        assertEquals(EngineState.DEGRADED, statuses.observeAll().first { it[EngineId.SYSTEM]?.message == "2 项待处理" }.getValue(EngineId.SYSTEM).state)
        pending.value = 0
        drifted.value = 1
        runCurrent()
        assertEquals(EngineState.DEGRADED, statuses.observeAll().first { it[EngineId.SYSTEM]?.message == "1 项待处理" }.getValue(EngineId.SYSTEM).state)
        assertEquals(applied, device.snapshot())
    }
}
