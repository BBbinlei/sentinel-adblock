package com.sentinel.vpn.packet

object PacketBuilder {
    private fun wrap(req: IpPacket, protocol: Int, body: ByteArray): ByteArray {
        val h = if (req.version == 4) ByteArray(20).also {
            it[0] = 0x45; put16(it, 2, 20 + body.size); it[8] = 64; it[9] = protocol.toByte()
            req.dst.copyInto(it, 12); req.src.copyInto(it, 16); put16(it, 10, Checksum.of(it))
        } else ByteArray(40).also {
            it[0] = 0x60; put16(it, 4, body.size); it[6] = protocol.toByte(); it[7] = 64
            req.dst.copyInto(it, 8); req.src.copyInto(it, 24)
        }
        return h + body
    }
    fun udpReply(req: IpPacket, payload: ByteArray): ByteArray {
        val b = ByteArray(8).also { put16(it, 0, req.dstPort); put16(it, 2, req.srcPort); put16(it, 4, 8 + payload.size) } + payload
        val c = Checksum.transport(req.dst, req.src, 17, b)
        put16(b, 6, if (c == 0) 65535 else c)
        return wrap(req, 17, b)
    }
    fun tcpRst(req: IpPacket): ByteArray {
        val h = if (req.version == 4) (req.raw[0].toInt() and 15) * 4 else 40
        val b = ByteArray(20).also {
            put16(it, 0, req.dstPort); put16(it, 2, req.srcPort)
            put32(it, 8, u32(req.raw, h + 4) + req.payloadLength + if (req.isTcpSyn) 1 else 0)
            it[12] = 0x50; it[13] = 0x14
        }
        put16(b, 16, Checksum.transport(req.dst, req.src, 6, b))
        return wrap(req, 6, b)
    }
    fun icmpPortUnreachable(req: IpPacket): ByteArray {
        val v4 = req.version == 4
        val quote = if (v4) minOf(req.length, (req.raw[0].toInt() and 15) * 4 + 8) else minOf(req.length, 1232)
        val b = ByteArray(8).also { it[0] = if (v4) 3 else 1; it[1] = if (v4) 3 else 4 } + req.raw.copyOf(quote)
        put16(b, 2, if (v4) Checksum.of(b) else Checksum.transport(req.dst, req.src, 58, b))
        return wrap(req, if (v4) 1 else 58, b)
    }
}
