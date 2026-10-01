package com.sentinel.vpn.fakes

import com.sentinel.vpn.packet.IpPacket
import com.sentinel.vpn.dns.UpstreamResolver
import com.sentinel.vpn.tun.*
import com.sentinel.vpn.decide.*
import com.sentinel.vpn.service.*
import java.io.*
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.test.*

// Pipe 保留真实阻塞读语义；长度队列仅保持 TUN「一次 read 一个报文」的边界。
class PipeInput : InputStream() {
    private val pipe = PipedInputStream(65536)
    private val writer = PipedOutputStream(pipe)
    private val lengths = LinkedBlockingQueue<Int>()
    @Volatile var closed = false; private set
    @Volatile var failure: IOException? = null
    fun feed(bytes: ByteArray) { check(!closed); writer.write(bytes); writer.flush(); lengths.put(bytes.size) }
    fun fail() { failure = IOException("injected TUN read failure"); lengths.put(-2) }
    override fun read(): Int = error("TUN 应使用报文缓冲区 read")
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = lengths.take()
        if (n == -2) throw assertNotNull(failure)
        if (n == -1) return -1
        require(n <= len)
        var read = 0
        while (read < n) { val count = pipe.read(b, off + read, n - read); if (count < 0) throw EOFException(); read += count }
        return n
    }
    override fun close() { if (!closed) { closed = true; lengths.put(-1); writer.close(); pipe.close() } }
}

class PacketOutput : OutputStream() {
    val packets = Channel<ByteArray>(Channel.UNLIMITED)
    private var pending = byteArrayOf()
    @Synchronized override fun write(b: ByteArray, off: Int, len: Int) {
        pending += b.copyOfRange(off, off + len)
        while (pending.size >= 6) {
            val v4 = pending[0].toInt() ushr 4 == 4
            val total = if (v4) Wire.u16(pending, 2) else 40 + Wire.u16(pending, 4)
            require(total > 0)
            if (pending.size < total) break
            check(packets.trySend(pending.copyOf(total)).isSuccess); pending = pending.copyOfRange(total, pending.size)
        }
    }
    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
    suspend fun next(): ByteArray = withContext(Dispatchers.IO) { withTimeout(5000) { packets.receive() } }
    override fun close() { packets.close() }
}

class FakeTunHandle(val number: Int, private val operations: MutableList<String>) : TunHandle {
    override val input = PipeInput()
    override val output = PacketOutput()
    @Volatile var closed = false; private set
    override fun close() {
        if (!closed) { closed = true; operations.add("close:$number"); input.close(); output.close() }
    }
}
class FakeTunFactory : TunFactory {
    val operations: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val specs: MutableList<TunSpec> = Collections.synchronizedList(mutableListOf())
    val handles: MutableList<FakeTunHandle> = Collections.synchronizedList(mutableListOf())
    var refuse = false
    override fun establish(spec: TunSpec): TunHandle? {
        specs.add(spec); operations.add("establish:${specs.size}")
        if (refuse) return null
        return FakeTunHandle(specs.size, operations).also { handles.add(it) }
    }
    val current get() = handles.last()
    fun close() { handles.forEach { it.close() } }
}
class FakeUpstream : UpstreamResolver {
    val queries: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())
    @Volatile var fail = false
    val failures = Channel<Unit>(Channel.UNLIMITED)
    @Volatile var failOnId: Int? = null
    override suspend fun resolve(query: ByteArray): ByteArray {
        queries.add(query.copyOf()); if (fail || failOnId == Wire.u16(query, 0)) { failures.trySend(Unit); throw IOException("injected upstream failure") }
        return Wire.answer(query)
    }
}
class FakeDecisions : DecisionSource {
    var rules = matcher(rule())
    var ctx = DnsContext(config(), emptySet(), false)
    var fail = false
    override fun matcher() = if (fail) throw IOException("injected decision failure") else rules
    override fun context(pkg: String?) = if (fail) throw IOException("injected decision failure") else ctx
}
fun dnsPayload(raw: ByteArray): ByteArray {
    val packet = assertNotNull(IpPacket.parse(raw, raw.size))
    return raw.copyOfRange(packet.payloadOffset, packet.payloadOffset + packet.payloadLength)
}
suspend fun exchange(tun: FakeTunHandle, query: ByteArray): ByteArray {
    tun.input.feed(Wire.packet(body = query)); return dnsPayload(tun.output.next())
}

class RecordedEvents {
    val all: MutableList<LoopEvent> = Collections.synchronizedList(mutableListOf())
    private val added = Channel<Unit>(Channel.UNLIMITED)
    fun record(event: LoopEvent) { all.add(event); added.trySend(Unit) }
    suspend fun awaitCount(count: Int) = withContext(Dispatchers.IO) {
        withTimeout(5000) { while (all.size < count) added.receive() }
    }
}
