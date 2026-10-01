package com.sentinel.vpn.service

import kotlinx.coroutines.test.runCurrent
import com.sentinel.data.contract.RewardWindowContract
import com.sentinel.data.db.*
import com.sentinel.rules.model.EventKind
import com.sentinel.vpn.fakes.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class VpnControllerTest {
    @Test fun `UT-VP-5-01 exclusions rebuild before old TUN closes`() = runTest { withController {
        start(); assertEquals(1, factory.handles.size)
        data.apps.setLevel("test.app", ProtectLevel.OFF); test.runCurrent()
        tick(1999); assertEquals(1, factory.handles.size); tick(1)
        assertEquals(2, factory.handles.size); assertTrue(factory.handles.first().closed); assertFalse(factory.current.closed)
        assertTrue(factory.operations.indexOf("establish:2") < factory.operations.indexOf("close:1"))
    } }
    @Test fun `UT-VP-5-02 newly registered sensitive App is excluded`() = runTest { withController {
        start(); data.app("new.bank.app", level = null, sensitive = true); test.runCurrent(); tick(2000)
        assertEquals(2, factory.handles.size); assertTrue("new.bank.app" in factory.specs.last().disallowed)
        assertTrue(factory.handles.first().closed); assertFalse(factory.current.closed)
    } }
    @Test fun `UT-VP-5-03 rule version hot swaps matcher without rebuilding TUN`() = runTest { withController {
        start(); Wire.assertDnsAnswer(query(), Wire.query(), ByteArray(4))
        val before = data.global.get().ruleVersion; data.install(rule("newads.example.test")); test.runCurrent()
        assertTrue(data.global.get().ruleVersion > before)
        assertContentEquals(Wire.answer(Wire.query()), query())
        Wire.assertDnsAnswer(query("newads.example.test"), Wire.query("newads.example.test"), ByteArray(4))
        assertEquals(1, factory.handles.size); assertFalse(factory.current.closed)
    } }
    @Test fun `UT-VP-5-04 tenth blocked event emits retry storm`() = runTest { withController {
        start(); repeat(9) { query(id = it) }; tick(2000)
        assertEquals(9, data.eventRows().count { it.kind == EventKind.DNS_BLOCKED }); assertTrue(data.signalRows().isEmpty())
        query(id = 9); tick(2000)
        assertEquals(10, data.eventRows().count { it.kind == EventKind.DNS_BLOCKED })
        val signal = data.signalRows().single(); assertEquals("test.app", signal.pkg)
        assertEquals(SignalKind.RETRY_STORM, signal.kind); assertEquals("dns:ads.example.test", signal.ruleId)
        query(id = 10); tick(2000); assertEquals(1, data.signalRows().size)
    } }
    @Test fun `UT-VP-5-06 DNS event detail preserves actual subdomain`() = runTest { withController {
        start(); val name = "slot.ads.example.test"; query(name); tick(2000)
        val blocked = data.eventRows().single(); assertEquals(name, blocked.detail); assertEquals("dns:ads.example.test", blocked.ruleId)
        data.app(level = null, observing = true); test.runCurrent(); query(name); tick(2000)
        val observing = data.eventRows().single { it.kind == EventKind.WOULD_BLOCK }
        assertEquals(name, observing.detail); assertEquals("dns:ads.example.test", observing.ruleId)
    } }
    @Test fun `UT-VP-5-07 empty and failed reward reads keep SDK blocked and loop running`() = runTest {
        for (failRead in listOf(false, true)) withController {
            if (failRead) data.sql("DROP TABLE reward_windows") // 在订阅之前制造 SELECT 失败；不是伪造空 Flow。
            start(); assertEquals(EngineState.RUNNING, controller.state.value)
            val state = controller.state.value
            repeat(2) { Wire.assertDnsAnswer(query("sdk.example.test", it), Wire.query("sdk.example.test", id = it), ByteArray(4)) }
            assertTrue(contexts.isNotEmpty()); assertTrue(contexts.all { !it.rewardWindowOpen })
            assertEquals(state, controller.state.value); assertFalse(factory.current.closed); assertEquals(0, stops.get())
            assertEquals(0, upstream.queries.size)
        }
        withController {
            start(); data.rewards.open("test.app"); test.runCurrent()
            assertContentEquals(Wire.answer(Wire.query("sdk.example.test")), query("sdk.example.test"))
            tick(RewardWindowContract.TTL_MS) // until == now 时窗口已关闭。
            Wire.assertDnsAnswer(query("sdk.example.test"), Wire.query("sdk.example.test"), ByteArray(4))
            assertFalse(contexts.last().rewardWindowOpen)
        }
    }
}
