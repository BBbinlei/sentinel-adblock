package com.sentinel.vpn

import com.sentinel.data.contract.RewardWindowContract
import com.sentinel.data.db.*
import com.sentinel.rules.model.EventKind
import com.sentinel.vpn.fakes.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class VpnModuleTest {
    @Test fun `MT-VP-01 standard observation reward pause resume script`() = runTest { withController {
        start()
        Wire.assertDnsAnswer(query(), Wire.query(), ByteArray(4)); tick(2000)
        assertEquals(EventKind.DNS_BLOCKED, data.eventRows().single().kind)
        data.app("observing.app", level = null, observing = true); pkg = "observing.app"; test.runCurrent()
        assertContentEquals(Wire.answer(Wire.query()), query()); tick(2000)
        val observing = data.eventRows().single { it.pkg == "observing.app" }
        assertEquals(EventKind.WOULD_BLOCK, observing.kind); assertEquals("ads.example.test", observing.detail)
        pkg = "test.app"; data.rewards.open("test.app"); test.runCurrent()
        assertContentEquals(Wire.answer(Wire.query("sdk.example.test")), query("sdk.example.test"))
        assertTrue(contexts.last().rewardWindowOpen)
        pkg = "other.app"; Wire.assertDnsAnswer(query("sdk.example.test"), Wire.query("sdk.example.test"), ByteArray(4))
        assertFalse(contexts.last().rewardWindowOpen) // 奖励窗口只对开窗 App 生效。
        pkg = "test.app"; Wire.assertDnsAnswer(query(), Wire.query(), ByteArray(4)) // AD 不在释放标签中。
        data.global.pauseFor(); test.runCurrent(); tick(2000)
        for (name in listOf("ads.example.test", "sdk.example.test", "normal.example.test")) assertContentEquals(Wire.answer(Wire.query(name)), query(name))
        assertTrue(contexts.last().cfg?.level == ProtectLevel.OFF)
        data.global.resume(); test.runCurrent(); tick(2000)
        Wire.assertDnsAnswer(query(), Wire.query(), ByteArray(4))
        tick(RewardWindowContract.TTL_MS)
        Wire.assertDnsAnswer(query("sdk.example.test"), Wire.query("sdk.example.test"), ByteArray(4))
        assertFalse(factory.current.closed); assertEquals(EngineState.RUNNING, controller.state.value)
    } }

    @Test fun `MT-VP-02 seeded thousand faults preserve loop and close on stop`() = runTest { withController {
        start()
        // 固定种子且三类都覆盖；每轮只有一个故障，并紧接一条健康查询作为继续运行证据。
        val schedule = List(1000) { it % 3 }.shuffled(Random(0x5e17))
        val injected = IntArray(3)
        suspend fun awaitFault(channel: Channel<Unit>) = withContext(Dispatchers.IO) { withTimeout(5000) { channel.receive() } }
        schedule.forEachIndexed { index, kind ->
            val id = index + 1
            when (kind) {
                0 -> {
                    upstream.failOnId = id
                    factory.current.input.feed(Wire.packet(body = Wire.query("normal.example.test", id = id)))
                    awaitFault(upstream.failures); upstream.failOnId = null
                }
                1 -> {
                    failDecisions = true
                    factory.current.input.feed(Wire.packet(body = Wire.query("normal.example.test", id = id)))
                    awaitFault(decisionFailures); failDecisions = false
                }
                2 -> {
                    data.failEventWrites(true)
                    try {
                        Wire.assertDnsAnswer(query("ads.example.test", id), Wire.query(id = id), ByteArray(4)); tick(2000)
                        // 写库失败已生效，不允许借断开整个 controller 来躲过异常。
                        assertFails { data.events.log("test.app", EventKind.DNS_BLOCKED, "dns:ads.example.test", "probe-failed-write") }
                    } finally { data.failEventWrites(false) }
                    val recovered = "after$index.ads.example.test"
                    Wire.assertDnsAnswer(query(recovered, id + 2000), Wire.query(recovered, id = id + 2000), ByteArray(4)); tick(2000)
                    assertTrue(data.eventRows().any { it.detail == recovered }, "写库故障恢复后应继续记录：$index")
                }
            }
            injected[kind]++
            // 故障包可以无应答；健康包必须收到相应 ID，排除读取到前一包的错误应答。
            val good = Wire.query("normal.example.test", id = id + 4000)
            factory.current.input.feed(Wire.packet(body = good))
            var reply = dnsPayload(factory.current.output.next())
            if (Wire.u16(reply, 0) == id) reply = dnsPayload(factory.current.output.next()) // 允许故障包返回 SERVFAIL。
            assertContentEquals(Wire.answer(good), reply, "fault=$kind index=$index")
            assertFalse(factory.current.closed); assertEquals(EngineState.RUNNING, controller.state.value)
        }
        assertEquals(1000, injected.sum()); assertTrue(injected.all { it >= 333 }); assertEquals(injected[1], decisionFaults.get())
        stop(); assertTrue(factory.handles.all { it.closed }); assertEquals(EngineState.STOPPED, controller.state.value)
        assertTrue(stops.get() > 0)
    } }
}
