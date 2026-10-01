package com.sentinel.vpn.packet

import com.sentinel.vpn.fakes.Wire
import kotlin.test.*
import org.junit.Test

class IpPacketTest {
    @Test fun `UT-VP-1-01 IPv4 UDP fields`() {
        val payload = byteArrayOf(1, 2, 3); val raw = Wire.packet(body = payload)
        val p = assertNotNull(IpPacket.parse(raw, raw.size))
        assertContentEquals(raw, p.raw); assertEquals(raw.size, p.length); assertEquals(4, p.version)
        assertEquals(Proto.UDP, p.proto); assertContentEquals(Wire.ip("10.111.0.1"), p.src); assertContentEquals(Wire.ip("10.111.0.2"), p.dst)
        assertEquals(42000, p.srcPort); assertEquals(53, p.dstPort); assertEquals(28, p.payloadOffset); assertEquals(3, p.payloadLength)
        assertContentEquals(payload, p.raw.copyOfRange(p.payloadOffset, p.payloadOffset + p.payloadLength)); assertFalse(p.isTcpSyn)
    }
    @Test fun `UT-VP-1-02 IPv6 UDP fields`() {
        val raw = Wire.packet(src = "fd11:1::1", dst = "fd11:1::2", body = byteArrayOf(8, 9), srcPort = 12345)
        val p = assertNotNull(IpPacket.parse(raw, raw.size))
        assertContentEquals(raw, p.raw); assertEquals(raw.size, p.length); assertEquals(6, p.version); assertEquals(Proto.UDP, p.proto)
        assertContentEquals(Wire.ip("fd11:1::1"), p.src); assertContentEquals(Wire.ip("fd11:1::2"), p.dst)
        assertEquals(12345, p.srcPort); assertEquals(53, p.dstPort); assertEquals(48, p.payloadOffset); assertEquals(2, p.payloadLength)
        assertContentEquals(byteArrayOf(8, 9), p.raw.copyOfRange(p.payloadOffset, p.payloadOffset + p.payloadLength)); assertFalse(p.isTcpSyn)
    }
    @Test fun `UT-VP-1-03 TCP SYN`() {
        val raw = Wire.packet(proto = 6, dstPort = 443, body = byteArrayOf())
        val p = assertNotNull(IpPacket.parse(raw, raw.size))
        assertEquals(Proto.TCP, p.proto); assertEquals(42000, p.srcPort); assertEquals(443, p.dstPort)
        assertEquals(2, p.tcpFlags); assertTrue(p.isTcpSyn); assertEquals(40, p.payloadOffset); assertEquals(0, p.payloadLength)
        val ack = Wire.packet(proto = 6, flags = 0x10, body = byteArrayOf())
        assertFalse(assertNotNull(IpPacket.parse(ack, ack.size)).isTcpSyn)
    }
    @Test fun `UT-VP-1-04 truncated packets are rejected`() {
        val packets = listOf(Wire.packet(body = byteArrayOf(1)), Wire.packet(src = "fd11:1::1", dst = "fd11:1::2", body = byteArrayOf(1)), Wire.packet(proto = 6, body = byteArrayOf(1)))
        for (raw in packets) for (length in 0 until raw.size) {
            assertNull(IpPacket.parse(raw, length), "有效缓冲区末尾之外不得读取：$length/${raw.size}")
            assertNull(IpPacket.parse(raw.copyOf(length), length))
        }
    }
}
