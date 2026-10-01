package com.sentinel.vpn.dns

import com.sentinel.vpn.fakes.Wire
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import kotlin.test.*
import org.junit.Test

class UpstreamResolverTest {
    private fun resolver(server: MockWebServer, udp: DatagramSocket, cache: DnsCache, protected: AtomicInteger = AtomicInteger(), datagrams: AtomicInteger = AtomicInteger()) =
        DohUdpResolver(server.url("/dns-query").toString(), InetSocketAddress("127.0.0.1", udp.localPort),
            SocketProtector { protected.incrementAndGet(); true }, { datagrams.incrementAndGet(); true }, cache)

    @Test fun `UT-VP-3-01 DoH POST returns response and populates cache`() = runBlocking {
        MockWebServer().use { server -> DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { udp ->
            val q = Wire.query(); val response = Wire.answer(q); val cache = DnsCache(clock = { 0L }); val sockets = AtomicInteger()
            server.enqueue(MockResponse().setHeader("Content-Type", "application/dns-message").setBody(Buffer().write(response)))
            assertContentEquals(response, resolver(server, udp, cache, sockets).resolve(q))
            assertContentEquals(response, assertNotNull(cache.get("ads.example.test", 1)))
            val request = assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            assertEquals("POST", request.method); assertEquals("/dns-query", request.path)
            assertEquals("application/dns-message", request.getHeader("Content-Type")); assertContentEquals(q, request.body.readByteArray())
            assertTrue(sockets.get() > 0, "DoH socket 必须 protect")
        } }
    }
    @Test fun `UT-VP-3-02 HTTP 500 falls back to protected UDP`() = runBlocking {
        MockWebServer().use { server -> DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { udp ->
            val executor = Executors.newSingleThreadExecutor(); val q = Wire.query(); val response = Wire.answer(q)
            udp.soTimeout = 5000
            val reply = executor.submit<ByteArray> {
                val received = DatagramPacket(ByteArray(4096), 4096); udp.receive(received)
                val bytes = received.data.copyOfRange(received.offset, received.offset + received.length)
                udp.send(DatagramPacket(response, response.size, received.socketAddress)); bytes
            }
            try {
                server.enqueue(MockResponse().setResponseCode(500)); val protected = AtomicInteger()
                assertContentEquals(response, resolver(server, udp, DnsCache(clock = { 0L }), datagrams = protected).resolve(q))
                assertContentEquals(q, reply.get(5, TimeUnit.SECONDS)); assertTrue(protected.get() > 0)
            } finally { udp.close(); executor.shutdownNow() }
        } }
    }
    @Test fun `UT-VP-3-03 failed DoH and silent UDP produce SERVFAIL with original ID`() = runBlocking {
        MockWebServer().use { server -> DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { udp ->
            server.enqueue(MockResponse().setResponseCode(500)) // UDP socket 保持绑定但不应答，确定触发 UDP 超时。
            val response = resolver(server, udp, DnsCache(clock = { 0L })).resolve(Wire.query(id = 0xabcd))
            assertEquals(0xabcd, Wire.u16(response, 0)); assertEquals(2, Wire.u16(response, 2) and 15)
            assertTrue(Wire.u16(response, 2) and 0x8000 != 0); assertEquals(0, Wire.u16(response, 6))
        } }
    }
    @Test fun `UT-VP-3-04 cache hit rewrites transaction ID without upstream access`() = runBlocking {
        MockWebServer().use { server -> DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { udp ->
            val first = Wire.query(id = 100); val response = Wire.answer(first); val cache = DnsCache(clock = { 0L })
            cache.put("ads.example.test", 1, response)
            val sockets = AtomicInteger(); val datagrams = AtomicInteger(); val upstream = resolver(server, udp, cache, sockets, datagrams)
            for (id in listOf(200, 300)) {
                val result = upstream.resolve(Wire.query(id = id))
                assertEquals(id, Wire.u16(result, 0)); assertContentEquals(response.copyOfRange(2, response.size), result.copyOfRange(2, result.size))
            }
            assertEquals(0, server.requestCount); assertEquals(0, sockets.get()); assertEquals(0, datagrams.get())
        } }
    }
}
