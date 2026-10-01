package com.sentinel.vpn.packet

import com.sentinel.vpn.fakes.Wire
import kotlin.test.*
import org.junit.Test

class PacketBuilderTest {
    @Test fun `UT-VP-1-05 UDP reply swaps endpoints and checksums`() {
        // RFC 1071 校验向量也验证测试 oracle，避免一个错误 oracle 让产品错误通过。
        assertEquals(0x220d, Wire.checksum(byteArrayOf(0, 1, 0xf2.toByte(), 3, 0xf4.toByte(), 0xf5.toByte(), 0xf6.toByte(), 0xf7.toByte())))
        for ((src, dst) in listOf("10.111.0.1" to "10.111.0.2", "fd11:1::1" to "fd11:1::2")) {
            val raw = Wire.packet(src, dst, body = byteArrayOf(1, 2))
            val req = assertNotNull(IpPacket.parse(raw, raw.size)); val payload = byteArrayOf(3, 4, 5) // odd checksum length
            val reply = PacketBuilder.udpReply(req, payload); val p = assertNotNull(IpPacket.parse(reply, reply.size))
            assertContentEquals(req.dst, p.src); assertContentEquals(req.src, p.dst)
            assertEquals(req.dstPort, p.srcPort); assertEquals(req.srcPort, p.dstPort); assertEquals(Proto.UDP, p.proto)
            assertContentEquals(payload, reply.copyOfRange(p.payloadOffset, p.payloadOffset + p.payloadLength)); Wire.assertChecksums(reply, 17)
        }
    }
    @Test fun `UT-VP-1-06 RST ACK accounts for payload and SYN`() {
        for ((src, dst) in listOf("10.111.0.1" to "10.111.0.2", "fd11:1::1" to "fd11:1::2"))
        for (flags in listOf(2, 0x10)) for (seq in listOf(0x10203040L, 0xffffffffL)) {
            val raw = Wire.packet(src = src, dst = dst, proto = 6, dstPort = 443, flags = flags, seq = seq, body = byteArrayOf(7, 8, 9))
            val req = assertNotNull(IpPacket.parse(raw, raw.size)); val rst = PacketBuilder.tcpRst(req)
            val p = assertNotNull(IpPacket.parse(rst, rst.size))
            assertEquals(0x14, p.tcpFlags); assertContentEquals(req.dst, p.src); assertContentEquals(req.src, p.dst)
            assertEquals(req.dstPort, p.srcPort); assertEquals(req.srcPort, p.dstPort)
            assertEquals((seq + 3 + if (flags == 2) 1 else 0) and 0xffffffffL, Wire.u32(rst, p.payloadOffset - 20 + 8))
            Wire.assertChecksums(rst, 6)
        }
    }
    @Test fun `UT-VP-1-07 ICMP quotes original header and IPv6 code`() {
        val raw = Wire.packet(dst = "203.107.1.33", body = byteArrayOf(1, 2, 3))
        val req = assertNotNull(IpPacket.parse(raw, raw.size)); val reply = PacketBuilder.icmpPortUnreachable(req)
        val p = assertNotNull(IpPacket.parse(reply, reply.size))
        assertEquals(Proto.ICMP, p.proto); assertEquals(3, reply[20].toInt() and 255); assertEquals(3, reply[21].toInt() and 255)
        assertContentEquals(raw.copyOfRange(0, 28), reply.copyOfRange(28, 56)); Wire.assertChecksums(reply, 1)
        val v6 = Wire.packet(src = "fd11:1::1", dst = "240e:928:1400:10::25", body = byteArrayOf(1))
        val r6 = PacketBuilder.icmpPortUnreachable(assertNotNull(IpPacket.parse(v6, v6.size)))
        assertEquals(Proto.ICMPV6, assertNotNull(IpPacket.parse(r6, r6.size)).proto)
        assertEquals(1, r6[40].toInt() and 255); assertEquals(4, r6[41].toInt() and 255); Wire.assertChecksums(r6, 58)
    }
}
