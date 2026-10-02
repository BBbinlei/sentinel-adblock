package com.sentinel.vpn.dns

import com.sentinel.vpn.packet.u16
import java.net.*
import java.io.IOException
import javax.net.SocketFactory
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

fun interface SocketProtector { fun protect(socket: Socket): Boolean }
interface UpstreamResolver { suspend fun resolve(query: ByteArray): ByteArray }
class DohUdpResolver(private val dohUrl: String, private val udpServer: InetSocketAddress,
    protector: SocketProtector, private val datagramProtector: (DatagramSocket) -> Boolean,
    private val cache: DnsCache, private val onTransportResult: (Boolean) -> Unit = {}) : UpstreamResolver {
    private val client = OkHttpClient.Builder().socketFactory(object : SocketFactory() {
        override fun createSocket(): Socket = Socket().also {
            if (!protector.protect(it)) { it.close(); throw IOException("Socket protection failed") }
        }
        private fun connected(remote: InetSocketAddress, local: InetSocketAddress? = null): Socket {
            val s = createSocket()
            try { if (local != null) s.bind(local); s.connect(remote, 3000); return s }
            catch (e: Exception) { s.close(); throw e }
        }
        override fun createSocket(host: String, port: Int) = connected(InetSocketAddress(host, port))
        override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int) = connected(InetSocketAddress(host, port), InetSocketAddress(local, localPort))
        override fun createSocket(host: InetAddress, port: Int) = connected(InetSocketAddress(host, port))
        override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int) = connected(InetSocketAddress(host, port), InetSocketAddress(local, localPort))
    }).callTimeout(3, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

    override suspend fun resolve(query: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val question = DnsMessage.parseQuestion(query, 0, query.size) ?: return@withContext DnsMessage.servFail(query)
        cache.get(question.name, question.qtype)?.let { return@withContext DnsMessage.withId(it, question.id) }
        fun valid(b: ByteArray): ByteArray {
            require(b.size in 12..65535 && u16(b, 2) and 0x8000 != 0)
            require(DnsMessage.parseQuestion(b, 0, b.size) == question)
            return b
        }
        val response = try {
            val request = Request.Builder().url(dohUrl).header("Accept", "application/dns-message")
                .post(query.toRequestBody("application/dns-message".toMediaType())).build()
            client.newCall(request).execute().use {
                require(it.isSuccessful)
                val source = requireNotNull(it.body).source()
                source.request(65536)
                require(source.buffer.size <= 65535)
                valid(source.readByteArray())
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            try {
                DatagramSocket().use { socket ->
                    require(datagramProtector(socket)); socket.soTimeout = 2000; socket.connect(udpServer)
                    socket.send(DatagramPacket(query, query.size))
                    val packet = DatagramPacket(ByteArray(65535), 65535); socket.receive(packet)
                    valid(packet.data.copyOfRange(packet.offset, packet.offset + packet.length))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onTransportResult(false)
                return@withContext DnsMessage.servFail(query)
            }
        }
        // 有效 DNS 错误应答（包括 SERVFAIL/NXDOMAIN）仍表示上游传输成功。
        onTransportResult(true)
        cache.put(question.name, question.qtype, response)
        response
    }
}
