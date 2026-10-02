package com.sentinel.vpn.service

import com.sentinel.data.db.*
import com.sentinel.data.repo.EngineStatusRepository
import com.sentinel.vpn.dns.*
import com.sentinel.vpn.fakes.*
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class UpstreamFailSafeTest {
    private val reason = "DNS 上游连续不可用，网络拦截已停止，网络已恢复直连"

    @Test fun `R01-01 three failed DoH and UDP transports close TUN before STOPPED`() = runTest {
        var tun: FakeTunHandle? = null
        var stoppedWrites = 0
        withController(statusFactory = { data ->
            val delegate = data.database.engineStatusDao()
            EngineStatusRepository(object : EngineStatusDao by delegate {
                override suspend fun upsert(entity: EngineStatusEntity) {
                    if (entity.state == EngineState.STOPPED) {
                        assertTrue(assertNotNull(tun).closed, "TUN must close before STOPPED write")
                        stoppedWrites++
                    }
                    delegate.upsert(entity)
                }
            }, data.clock)
        }) {
            start(); tun = factory.current; controller.onUnderlyingNetwork(true)
            MockWebServer().use { server -> DatagramSocket(0, InetAddress.getLoopbackAddress()).use { udp ->
                val results = mutableListOf<Boolean>()
                val resolver = DohUdpResolver(server.url("/dns-query").toString(),
                    InetSocketAddress(InetAddress.getLoopbackAddress(), udp.localPort), { true }, { true },
                    DnsCache(clock = clock::now), { results.add(it); controller.onUpstreamTransport(it) })
                repeat(3) { i ->
                    server.enqueue(MockResponse().setResponseCode(500)) // Bound UDP never replies: real timeout.
                    val response = resolver.resolve(Wire.query("failure$i.example.test", id = i))
                    assertEquals(i, Wire.u16(response, 0)); assertEquals(2, Wire.u16(response, 2) and 15)
                    assertEquals(i == 2, factory.current.closed)
                    if (i < 2) assertEquals(EngineState.RUNNING, data.vpnStatus()?.state)
                }
                assertEquals(listOf(false, false, false), results); assertEquals(3, server.requestCount)
                test.runCurrent()
                assertEquals(EngineState.STOPPED, controller.state.value)
                assertEquals(reason, data.vpnStatus()?.message); assertEquals(1, stops.get()); assertEquals(1, stoppedWrites)
                assertTrue(factory.operations.indexOf("close:1") < factory.operations.indexOf("stopService"))
            } }
        }
    }

    @Test fun `R01-02 valid DoH and UDP NOERROR SERVFAIL NXDOMAIN reset failures`() = runTest {
        for (viaUdp in listOf(false, true)) for (rcode in listOf(0, 2, 3)) withController {
            start(); controller.onUnderlyingNetwork(true); repeat(2) { controller.onUpstreamTransport(false) }
            MockWebServer().use { server -> DatagramSocket(0, InetAddress.getLoopbackAddress()).use { udp ->
                val query = Wire.query("valid.example.test")
                val response = query.copyOf().also { Wire.put16(it, 2, 0x8180 or rcode) }
                val executor = Executors.newSingleThreadExecutor()
                udp.soTimeout = 5000
                val reply = if (viaUdp) executor.submit<ByteArray> {
                    val received = DatagramPacket(ByteArray(4096), 4096); udp.receive(received)
                    udp.send(DatagramPacket(response, response.size, received.socketAddress))
                    received.data.copyOf(received.length)
                } else null
                try {
                    server.enqueue(if (viaUdp) MockResponse().setResponseCode(500)
                        else MockResponse().setBody(Buffer().write(response)))
                    val results = mutableListOf<Boolean>()
                    val resolver = DohUdpResolver(server.url("/dns-query").toString(),
                        InetSocketAddress(InetAddress.getLoopbackAddress(), udp.localPort), { true }, { true },
                        DnsCache(clock = clock::now), { results.add(it); controller.onUpstreamTransport(it) })
                    assertContentEquals(response, resolver.resolve(query))
                    reply?.let { assertContentEquals(query, it.get(5, TimeUnit.SECONDS)) }
                    assertEquals(listOf(true), results)
                    repeat(2) { controller.onUpstreamTransport(false) }
                    assertFalse(factory.current.closed, "UDP=$viaUdp RCODE=$rcode must reset consecutive failures")
                    controller.onUpstreamTransport(false); assertTrue(factory.current.closed)
                    test.runCurrent(); assertEquals(reason, data.vpnStatus()?.message)
                } finally { udp.close(); executor.shutdownNow() }
            } }
        }
    }

    @Test fun `R01-03 unknown and offline failures do not count and recovery clears count`() = runTest { withController {
        start(); repeat(5) { controller.onUpstreamTransport(false) }
        assertFalse(factory.current.closed)
        controller.onUnderlyingNetwork(true); repeat(2) { controller.onUpstreamTransport(false) }
        controller.onUnderlyingNetwork(false); repeat(5) { controller.onUpstreamTransport(false) }
        assertFalse(factory.current.closed)
        controller.onUnderlyingNetwork(true); repeat(2) { controller.onUpstreamTransport(false) }
        assertFalse(factory.current.closed); assertEquals(EngineState.RUNNING, data.vpnStatus()?.state)
        controller.onUnderlyingNetwork(true) // Duplicate available notification must not reset.
        controller.onUpstreamTransport(false); assertTrue(factory.current.closed)
        test.runCurrent(); assertEquals(reason, data.vpnStatus()?.message)
    } }

    @Test fun `R01-04 cache hit emits no transport result and preserves failure count`() = runTest { withController {
        start(); controller.onUnderlyingNetwork(true); repeat(2) { controller.onUpstreamTransport(false) }
        MockWebServer().use { server ->
            val query = Wire.query("cached.example.test"); val answer = Wire.answer(query)
            val cache = DnsCache(clock = clock::now); cache.put("cached.example.test", 1, answer)
            val results = mutableListOf<Boolean>()
            val resolver = DohUdpResolver(server.url("/dns-query").toString(), InetSocketAddress("127.0.0.1", 53),
                { error("cache must not open DoH socket") }, { error("cache must not open UDP socket") }, cache,
                { results.add(it); controller.onUpstreamTransport(it) })
            val result = resolver.resolve(Wire.query("cached.example.test", id = 99))
            assertEquals(99, Wire.u16(result, 0)); assertContentEquals(answer.drop(2), result.drop(2))
            assertTrue(results.isEmpty()); assertEquals(0, server.requestCount); assertFalse(factory.current.closed)
            controller.onUpstreamTransport(false); assertTrue(factory.current.closed)
            test.runCurrent(); assertEquals(reason, data.vpnStatus()?.message)
        }
    } }
}
