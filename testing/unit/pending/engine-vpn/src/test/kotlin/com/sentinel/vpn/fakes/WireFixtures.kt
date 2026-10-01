package com.sentinel.vpn.fakes

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import com.sentinel.rules.domain.DomainCompiler
import com.sentinel.rules.domain.DomainMatcher
import com.sentinel.rules.model.*
import com.sentinel.data.db.*
import com.sentinel.data.policy.EffectiveConfig
import kotlin.test.*

// 独立线格式 oracle；不调用被测 PacketBuilder、DnsMessage 或 Checksum 生成期望值。
object Wire {
    fun ip(s: String): ByteArray = InetAddress.getByName(s).address // 只传数字地址，不解析域名。
    fun u16(b: ByteArray, p: Int) = ((b[p].toInt() and 255) shl 8) or (b[p + 1].toInt() and 255)
    fun u32(b: ByteArray, p: Int): Long = (0..3).fold(0L) { n, i -> (n shl 8) or (b[p + i].toLong() and 255) }
    fun put16(b: ByteArray, p: Int, n: Int) { b[p] = (n ushr 8).toByte(); b[p + 1] = n.toByte() }
    fun put32(b: ByteArray, p: Int, n: Long) { (0..3).forEach { b[p + it] = (n ushr (24 - it * 8)).toByte() } }
    fun checksum(b: ByteArray): Int {
        var sum = 0L
        for (p in b.indices step 2) sum += ((b[p].toInt() and 255) shl 8) + if (p + 1 < b.size) b[p + 1].toInt() and 255 else 0
        while (sum ushr 16 != 0L) sum = (sum and 65535) + (sum ushr 16)
        return sum.inv().toInt() and 65535
    }
    private fun pseudo(src: ByteArray, dst: ByteArray, proto: Int, n: Int): ByteArray =
        src + dst + if (src.size == 4) byteArrayOf(0, proto.toByte(), (n ushr 8).toByte(), n.toByte())
        else ByteArray(8).also { put32(it, 0, n.toLong()); it[7] = proto.toByte() }

    fun packet(src: String = "10.111.0.1", dst: String = "10.111.0.2", proto: Int = 17,
               body: ByteArray, srcPort: Int = 42000, dstPort: Int = 53, flags: Int = 2, seq: Long = 0x10203040): ByteArray {
        val s = ip(src); val d = ip(dst); require(s.size == d.size)
        val segment = when (proto) {
            17 -> ByteArray(8).also { put16(it, 0, srcPort); put16(it, 2, dstPort); put16(it, 4, 8 + body.size) } + body
            6 -> ByteArray(20).also { put16(it, 0, srcPort); put16(it, 2, dstPort); put32(it, 4, seq); it[12] = 0x50; it[13] = flags.toByte(); put16(it, 14, 65535) } + body
            else -> body.copyOf()
        }
        if (proto == 17 || proto == 6) {
            val c = checksum(pseudo(s, d, proto, segment.size) + segment)
            put16(segment, if (proto == 17) 6 else 16, if (c == 0 && proto == 17) 65535 else c)
        }
        val header = if (s.size == 4) ByteArray(20).also {
            it[0] = 0x45; put16(it, 2, 20 + segment.size); put16(it, 4, 0x1234); it[8] = 64; it[9] = proto.toByte()
            s.copyInto(it, 12); d.copyInto(it, 16); put16(it, 10, checksum(it))
        } else ByteArray(40).also {
            it[0] = 0x60; put16(it, 4, segment.size); it[6] = proto.toByte(); it[7] = 64
            s.copyInto(it, 8); d.copyInto(it, 24)
        }
        return header + segment
    }
    fun query(name: String = "ads.example.test", type: Int = 1, id: Int = 0x1234): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ByteArray(12).also { put16(it, 0, id); put16(it, 2, 0x0100); put16(it, 4, 1) })
        name.split('.').forEach { out.write(it.length); out.write(it.toByteArray(Charsets.US_ASCII)) }
        out.write(0); out.write(byteArrayOf((type ushr 8).toByte(), type.toByte(), 0, 1))
        return out.toByteArray()
    }
    fun answer(query: ByteArray, address: ByteArray = byteArrayOf(1, 2, 3, 4), ttl: Int = 120): ByteArray =
        query.copyOf().also { put16(it, 2, 0x8180); put16(it, 6, 1) } +
            ByteArray(12).also { it[0] = 0xc0.toByte(); it[1] = 12; put16(it, 2, if (address.size == 16) 28 else 1); put16(it, 4, 1); put32(it, 6, ttl.toLong()); put16(it, 10, address.size) } + address
    fun questionEnd(msg: ByteArray): Int {
        var p = 12
        while (msg[p] != 0.toByte()) {
            if (msg[p].toInt() and 0xc0 == 0xc0) return p + 6
            p += 1 + (msg[p].toInt() and 255)
        }
        return p + 5
    }
    fun assertDnsAnswer(msg: ByteArray, query: ByteArray, address: ByteArray?, ttl: Int = 60) {
        assertEquals(u16(query, 0), u16(msg, 0)); assertEquals(0, u16(msg, 2) and 15)
        assertTrue(u16(msg, 2) and 0x8000 != 0); assertEquals(1, u16(msg, 4))
        assertContentEquals(query.copyOfRange(12, questionEnd(query)), msg.copyOfRange(12, questionEnd(msg)))
        if (address == null) { assertEquals(0, u16(msg, 6)); assertEquals(questionEnd(msg), msg.size); return }
        assertEquals(1, u16(msg, 6)); var p = questionEnd(msg)
        if (msg[p].toInt() and 0xc0 == 0xc0) p += 2 else { while (msg[p] != 0.toByte()) p += 1 + (msg[p].toInt() and 255); p++ }
        assertEquals(u16(query, questionEnd(query) - 4), u16(msg, p)); assertEquals(1, u16(msg, p + 2))
        assertEquals(ttl.toLong(), u32(msg, p + 4)); assertEquals(address.size, u16(msg, p + 8))
        assertContentEquals(address, msg.copyOfRange(p + 10, p + 10 + address.size))
    }
    fun assertChecksums(raw: ByteArray, proto: Int) {
        val v4 = raw[0].toInt() ushr 4 == 4
        val h = if (v4) (raw[0].toInt() and 15) * 4 else 40
        if (v4) assertEquals(0, checksum(raw.copyOfRange(0, h)), "IPv4 checksum")
        val src = raw.copyOfRange(if (v4) 12 else 8, if (v4) 16 else 24)
        val dst = raw.copyOfRange(if (v4) 16 else 24, if (v4) 20 else 40)
        val segment = raw.copyOfRange(h, raw.size)
        assertEquals(0, checksum(if (proto == 1) segment else pseudo(src, dst, proto, segment.size) + segment), "transport checksum")
        if (proto == 17) assertNotEquals(0, u16(segment, 6), "不能把零 UDP 校验和值当作校验通过")
    }
}

fun matcher(vararg rules: DnsRule) = DomainMatcher(ByteBuffer.wrap(DomainCompiler.compile(rules.toList())))
fun rule(name: String = "ads.example.test", tag: DomainTag = DomainTag.AD, level: RuleLevel = RuleLevel.STANDARD) =
    DnsRule("dns:$name", name, tag, level, "test")
fun config(pkg: String = "test.app", level: ProtectLevel = ProtectLevel.STANDARD, observing: Boolean = false) =
    EffectiveConfig(pkg, level, observing, true, RewardedMode.SILENT, true, true, true, false, false)
