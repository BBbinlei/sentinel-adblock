package com.sentinel.vpn.packet

enum class Proto { TCP, UDP, ICMP, ICMPV6, OTHER }
class IpPacket(val raw: ByteArray, val length: Int, val version: Int, val proto: Proto,
    val src: ByteArray, val dst: ByteArray, val srcPort: Int, val dstPort: Int,
    val payloadOffset: Int, val payloadLength: Int, val tcpFlags: Int) {
    val isTcpSyn get() = proto == Proto.TCP && tcpFlags and 2 != 0
    companion object {
        fun parse(buf: ByteArray, len: Int): IpPacket? {
            if (len < 1 || len > buf.size) return null
            val version = (buf[0].toInt() and 255) ushr 4
            val h: Int; val total: Int; val protocol: Int; val src: ByteArray; val dst: ByteArray
            when (version) {
                4 -> {
                    if (len < 20) return null
                    h = (buf[0].toInt() and 15) * 4; total = u16(buf, 2)
                    if (h < 20 || total < h || total > len || u16(buf, 6) and 0x3fff != 0) return null
                    protocol = buf[9].toInt() and 255; src = buf.copyOfRange(12, 16); dst = buf.copyOfRange(16, 20)
                }
                6 -> {
                    if (len < 40) return null
                    h = 40; total = 40 + u16(buf, 4)
                    if (total > len) return null
                    protocol = buf[6].toInt() and 255; src = buf.copyOfRange(8, 24); dst = buf.copyOfRange(24, 40)
                }
                else -> return null
            }
            val proto = when (protocol) { 6 -> Proto.TCP; 17 -> Proto.UDP; 1 -> Proto.ICMP; 58 -> Proto.ICMPV6; else -> Proto.OTHER }
            var off = h; var end = total; var sport = 0; var dport = 0; var flags = 0
            if (proto == Proto.UDP || proto == Proto.TCP) {
                if (total - h < if (proto == Proto.UDP) 8 else 20) return null
                sport = u16(buf, h); dport = u16(buf, h + 2)
                if (proto == Proto.UDP) {
                    val size = u16(buf, h + 4)
                    if (size < 8 || h + size > total) return null
                    off += 8; end = h + size
                } else {
                    val size = ((buf[h + 12].toInt() and 255) ushr 4) * 4
                    if (size < 20 || h + size > total) return null
                    off += size; flags = buf[h + 13].toInt() and 255
                }
            }
            return IpPacket(buf.copyOf(total), total, version, proto, src, dst, sport, dport, off, end - off, flags)
        }
    }
}
