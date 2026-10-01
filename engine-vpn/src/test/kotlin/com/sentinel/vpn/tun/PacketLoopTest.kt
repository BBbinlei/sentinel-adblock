package com.sentinel.vpn.tun

import com.sentinel.vpn.fakes.*
import com.sentinel.vpn.packet.*
import com.sentinel.vpn.decide.*
import kotlinx.coroutines.*
import kotlin.test.*
import org.junit.Test

class PacketLoopTest {
    private suspend fun scenario(block: suspend (FakeTunHandle, FakeUpstream, RecordedEvents) -> Unit) = coroutineScope {
        val factory = FakeTunFactory(); val tun = factory.establish(TunSpec.build("self", emptySet())) as FakeTunHandle
        val upstream = FakeUpstream(); val events = RecordedEvents()
        val loop = PacketLoop(tun.input, tun.output, upstream, FakeDecisions(), PackageResolver { "test.app" }, events::record)
        val job = async(Dispatchers.IO) { loop.run() }
        try { block(tun, upstream, events); tun.input.close(); withTimeout(5000) { job.await() } }
        finally { factory.close(); job.cancelAndJoin() }
    }
    @Test fun `UT-VP-4-02 ad query gets zero address`() = runBlocking { scenario { tun, upstream, events ->
        val q = Wire.query(); Wire.assertDnsAnswer(exchange(tun, q), q, ByteArray(4)); assertEquals(0, upstream.queries.size)
        events.awaitCount(1); assertEquals(DnsVerdict.BLOCK, events.all.filterIsInstance<LoopEvent.Dns>().single().decision.verdict)
    } }
    @Test fun `UT-VP-4-03 normal query gets upstream answer`() = runBlocking { scenario { tun, upstream, _ ->
        val q = Wire.query("normal.example.test"); assertContentEquals(Wire.answer(q), exchange(tun, q)); assertEquals(1, upstream.queries.size)
    } }
    @Test fun `UT-VP-4-04 HttpDNS SYN rejected with RST`() = runBlocking { scenario { tun, upstream, events ->
        tun.input.feed(Wire.packet(dst = "203.107.1.33", dstPort = 443, proto = 6, body = byteArrayOf()))
        val response = tun.output.next(); val p = assertNotNull(IpPacket.parse(response, response.size))
        assertEquals(0x14, p.tcpFlags); assertEquals(443, p.srcPort); assertEquals(42000, p.dstPort)
        assertEquals(0x10203041L, Wire.u32(response, 28)); assertEquals(0, upstream.queries.size)
        events.awaitCount(1); assertEquals("203.107.1.33", events.all.filterIsInstance<LoopEvent.HttpDnsRejected>().single().ip)
    } }
    @Test fun `UT-VP-4-05 HttpDNS UDP rejected with ICMP`() = runBlocking { scenario { tun, upstream, _ ->
        tun.input.feed(Wire.packet(dst = "203.107.1.33", dstPort = 443, body = byteArrayOf(1)))
        val response = tun.output.next(); assertEquals(Proto.ICMP, assertNotNull(IpPacket.parse(response, response.size)).proto)
        assertEquals(3, response[20].toInt() and 255); assertEquals(3, response[21].toInt() and 255); assertEquals(0, upstream.queries.size)
    } }
    @Test fun `UT-VP-4-06 malformed packet is ignored and next packet works`() = runBlocking { scenario { tun, upstream, events ->
        tun.input.feed(byteArrayOf(0x45, 0, 0)); tun.input.feed(Wire.packet(body = byteArrayOf(1, 2, 3)))
        val q = Wire.query("normal.example.test"); assertContentEquals(Wire.answer(q), exchange(tun, q))
        assertEquals(1, upstream.queries.size); events.awaitCount(1); assertEquals(1, events.all.size)
    } }
    @Test fun `UT-VP-4-07 closed input lets run return normally`() = runBlocking {
        val input = PipeInput(); val output = PacketOutput()
        val job = async(Dispatchers.IO) { PacketLoop(input, output, FakeUpstream(), FakeDecisions(), PackageResolver { null }, {}).run() }
        input.close()
        try { withTimeout(5000) { job.await() }; assertTrue(job.isCompleted); assertFalse(job.isCancelled) }
        finally { input.close(); output.close(); job.cancelAndJoin() }
    }
}
