package com.sentinel.regression.contract.fakes

import com.sentinel.a11y.core.KeyValueStore
import com.sentinel.a11y.core.VolumePort
import com.sentinel.data.Clock
import com.sentinel.system.shell.*
import com.sentinel.vpn.dns.*
import com.sentinel.vpn.packet.IpPacket
import com.sentinel.vpn.service.TunFactory
import com.sentinel.vpn.service.TunHandle
import com.sentinel.vpn.tun.*
import java.io.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

class MutableClock(var time: Long = 1_800_000_000_000L) : Clock {
    override fun now(): Long = time
}
class MemoryVolume : VolumePort, KeyValueStore {
    private var volume = 7
    private val values = mutableMapOf<String, Int>()
    override fun get() = volume
    override fun set(v: Int) { volume = v }
    override fun getInt(k: String) = values[k]
    override fun putInt(k: String, v: Int) { values[k] = v }
    override fun remove(k: String) { values.remove(k) }
}
class ReadyApi : ShizukuApi {
    var ready = true
    override fun pingBinder() = ready
    override fun checkSelfPermission() = ready
    override fun isInstalled() = true
}
class FixtureShell : Shell {
    var dropboxOutput = ""
    override suspend fun exec(cmd: String) = ExecResult(0,
        if (cmd.contains("dropbox")) dropboxOutput else "", "")
    fun binder() = object : IShellService.Stub() {
        override fun exec(cmd: String?): String = buildJsonObject {
            put("exitCode", 0)
            put("stdout", if (cmd.orEmpty().contains("dropbox")) dropboxOutput else "")
            put("stderr", "")
        }.toString()
    }
}

// No sockets, platform VPN, or real DNS requests are used.
class RecordingUpstream : UpstreamResolver {
    val calls = AtomicInteger()
    override suspend fun resolve(query: ByteArray): ByteArray {
        calls.incrementAndGet()
        return DnsMessage.blockedResponse(query)
    }
}
class PipeTun : TunHandle {
    private val pipe = PipedInputStream(65_536)
    private val writer = PipedOutputStream(pipe)
    private val replies = Channel<ByteArray>(Channel.UNLIMITED)
    private val pending = ByteArrayOutputStream()
    override val input: InputStream = pipe
    override val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) { write(byteArrayOf(b.toByte()), 0, 1) }
        @Synchronized override fun write(b: ByteArray, off: Int, len: Int) {
            pending.write(b, off, len)
            var bytes = pending.toByteArray()
            while (bytes.size >= 20) {
                val size = ((bytes[2].toInt() and 255) shl 8) or (bytes[3].toInt() and 255)
                require(size >= 20) { "Malformed output IP packet" }
                if (bytes.size < size) break
                check(replies.trySend(bytes.copyOfRange(0, size)).isSuccess)
                bytes = bytes.copyOfRange(size, bytes.size)
            }
            pending.reset()
            pending.write(bytes)
        }
    }
    suspend fun query(domain: String, id: Int): ByteArray = withContext(Dispatchers.IO) {
        writer.write(dnsPacket(domain, id))
        writer.flush()
        withTimeout(5_000) { replies.receive() }
    }
    override fun close() {
        writer.close()
        pipe.close()
        replies.close()
    }
}
class RecordingTunFactory : TunFactory {
    val handles = mutableListOf<PipeTun>()
    override fun establish(spec: TunSpec): TunHandle = PipeTun().also { handles += it }
    val current get() = handles.last()
    fun close() { handles.forEach { it.close() } }
}
class MutablePackageResolver(var pkg: String) : PackageResolver {
    override fun pkgFor(pkt: IpPacket) = pkg
}

// Small IPv4/UDP DNS query builder; the test goes through the production PacketLoop.
private fun dnsPacket(domain: String, id: Int): ByteArray {
    val dns = ByteArrayOutputStream()
    DataOutputStream(dns).use { out ->
        out.writeShort(id); out.writeShort(0x0100); out.writeShort(1)
        repeat(3) { out.writeShort(0) }
        for (label in domain.split('.')) {
            out.writeByte(label.length); out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.writeByte(0); out.writeShort(1); out.writeShort(1)
    }
    val question = dns.toByteArray()
    val bytes = ByteBuffer.allocate(28 + question.size)
    bytes.put(0x45.toByte()).put(0).putShort((28 + question.size).toShort())
    bytes.putShort(id.toShort()).putShort(0).put(64).put(17).putShort(0)
    bytes.put(byteArrayOf(10, 111, 0, 1)).put(byteArrayOf(10, 111, 0, 2))
    bytes.putShort(40_000.toShort()).putShort(53).putShort((8 + question.size).toShort()).putShort(0)
    bytes.put(question)
    val packet = bytes.array()
    var sum = 0
    for (i in 0 until 20 step 2) sum += ((packet[i].toInt() and 255) shl 8) or (packet[i + 1].toInt() and 255)
    while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
    bytes.putShort(10, sum.inv().toShort())
    return packet
}
