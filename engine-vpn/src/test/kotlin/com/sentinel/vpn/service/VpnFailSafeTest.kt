package com.sentinel.vpn.service

import com.sentinel.data.db.*
import com.sentinel.vpn.fakes.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class VpnFailSafeTest {
    @Test fun `UT-VP-7-01 read failure closes TUN before STOPPED report`() = runTest { withController {
        start(); factory.current.input.fail()
        // 真实 IO 线程上的异常由 controller 收敛；等待状态 Flow，不能用 sleep。
        withContext(Dispatchers.IO) { withTimeout(5000) { controller.state.first { it == EngineState.STOPPED } } }; test.runCurrent()
        assertTrue(factory.current.closed); assertEquals(EngineState.STOPPED, controller.state.value)
        assertEquals("网络拦截意外停止，网络已恢复直连", data.vpnStatus()?.message)
    } }
    @Test fun `UT-VP-7-02 revoked authorization closes TUN and reports reason`() = runTest { withController {
        start(); controller.onRevoked(); test.runCurrent()
        assertTrue(factory.current.closed); assertEquals(EngineState.STOPPED, controller.state.value)
        assertEquals("VPN 授权被撤销或被其他 VPN 取代", data.vpnStatus()?.message)
    } }
    @Test fun `UT-VP-7-03 establish refusal is NOT_SETUP`() = runTest { withController {
        factory.refuse = true; start()
        assertEquals(EngineState.NOT_SETUP, controller.state.value); assertEquals(EngineState.NOT_SETUP, data.vpnStatus()?.state)
        assertTrue(factory.handles.isEmpty())
    } }
    @Test fun `UT-VP-7-04 unavailable rules forward while degraded`() = runTest { withController {
        start(installRules = false)
        assertEquals(EngineState.DEGRADED, controller.state.value)
        assertEquals("规则不可用，暂停网络拦截", data.vpnStatus()?.message)
        for (name in listOf("ads.example.test", "sdk.example.test", "normal.example.test")) assertContentEquals(Wire.answer(Wire.query(name)), query(name))
        assertTrue(contexts.isNotEmpty()); assertEquals(3, upstream.queries.size); assertFalse(factory.current.closed); assertEquals(0, stops.get())
    } }
    @Test fun `UT-VP-7-05 every exit calls stop service after closing all TUN handles`() = runTest {
        for (path in listOf("loop", "revoke", "establish", "stop")) withController {
            if (path == "establish") factory.refuse = true
            start()
            when (path) {
                "loop" -> {
                    factory.current.input.fail()
                    withContext(Dispatchers.IO) { withTimeout(5000) { controller.state.first { it == EngineState.STOPPED } } }; test.runCurrent()
                }
                "revoke" -> { controller.onRevoked(); test.runCurrent() }
                "stop" -> stop()
            }
            assertTrue(stops.get() > 0, path); assertTrue(factory.handles.all { it.closed }, path)
            val callback = factory.operations.indexOf("stopService")
            factory.handles.forEach { assertTrue(factory.operations.indexOf("close:${it.number}") in 0 until callback, path) }
        }
    }
}
