package com.sentinel.system.shell

import com.sentinel.system.fakes.FakeShizukuApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class ShizukuGatewayTest {
    // UT-SY-2-01
    @Test fun UT_SY_2_01_four_states_and_flow_initial_value() {
        val cases = listOf(
            FakeShizukuApi(false, false, false) to ShizukuState.NOT_INSTALLED,
            FakeShizukuApi(true, false, false) to ShizukuState.NOT_RUNNING,
            FakeShizukuApi(true, true, false) to ShizukuState.NO_PERMISSION,
            FakeShizukuApi(true, true, true) to ShizukuState.READY,
        )
        for ((api, expected) in cases) {
            val gateway = ShizukuGateway(api) { error("State query must not bind") }
            assertEquals(expected, gateway.state())
            assertEquals(expected, gateway.stateFlow.value)
        }
    }
    // UT-SY-2-02
    @Test fun UT_SY_2_02_non_ready_never_calls_binder() = runTest {
        var bindings = 0
        for (api in listOf(FakeShizukuApi(false), FakeShizukuApi(running = false), FakeShizukuApi(permitted = false))) {
            val gateway = ShizukuGateway(api) { bindings++; error("Must not bind") }
            val result = gateway.exec("settings put secure recommend 0")
            assertEquals(-2, result.exitCode)
            assertEquals("shizuku not ready", result.stderr)
            assertFalse(result.ok)
        }
        assertEquals(0, bindings)
    }
    // UT-SY-2-03
    @Test fun UT_SY_2_03_decode_success_and_failure_json() = runTest {
        val commands = mutableListOf<String>()
        val responses = ArrayDeque(listOf(
            """{"exitCode":0,"stdout":"line one\n\"quoted\"","stderr":""}""",
            """{"exitCode":7,"stdout":"partial","stderr":"permission denied"}""",
        ))
        val binder = object : IShellService.Stub() {
            override fun exec(cmd: String): String { commands += cmd; return responses.removeFirst() }
        }
        val gateway = ShizukuGateway(FakeShizukuApi()) { binder }
        val success = gateway.exec("settings get secure recommend")
        assertEquals(ExecResult(0, "line one\n\"quoted\"", ""), success)
        assertTrue(success.ok)
        val failure = gateway.exec("settings put secure recommend 0")
        assertEquals(ExecResult(7, "partial", "permission denied"), failure)
        assertFalse(failure.ok)
        assertEquals(listOf("settings get secure recommend", "settings put secure recommend 0"), commands)
    }
    // UT-SY-2-04
    @Test fun UT_SY_2_04_suspended_binding_times_out_at_15_seconds() = runTest {
        var bindings = 0
        val gateway = ShizukuGateway(FakeShizukuApi()) { bindings++; awaitCancellation() }
        val pending = async { gateway.exec("settings get secure recommend") }
        runCurrent()
        advanceTimeBy(14_999)
        assertFalse(pending.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        val result = pending.await()
        assertEquals(-1, result.exitCode)
        assertFalse(result.ok)
        assertEquals(1, bindings)
    }
}
