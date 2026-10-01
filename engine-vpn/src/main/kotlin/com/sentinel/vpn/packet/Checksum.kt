package com.sentinel.vpn.packet

internal fun u16(b: ByteArray, p: Int) = ((b[p].toInt() and 255) shl 8) or (b[p + 1].toInt() and 255)
internal fun u32(b: ByteArray, p: Int) = (0..3).fold(0L) { n, i -> (n shl 8) or (b[p + i].toLong() and 255) }
internal fun put16(b: ByteArray, p: Int, n: Int) { b[p] = (n ushr 8).toByte(); b[p + 1] = n.toByte() }
internal fun put32(b: ByteArray, p: Int, n: Long) { repeat(4) { b[p + it] = (n ushr (24 - it * 8)).toByte() } }
internal object Checksum {
    fun of(b: ByteArray): Int {
        var sum = 0L
        for (i in b.indices step 2) sum += ((b[i].toInt() and 255) shl 8) + if (i + 1 < b.size) b[i + 1].toInt() and 255 else 0
        while (sum ushr 16 != 0L) sum = (sum and 65535) + (sum ushr 16)
        return sum.inv().toInt() and 65535
    }
    fun transport(src: ByteArray, dst: ByteArray, protocol: Int, body: ByteArray): Int {
        val tail = if (src.size == 4) byteArrayOf(0, protocol.toByte(), (body.size ushr 8).toByte(), body.size.toByte())
        else ByteArray(8).also { put32(it, 0, body.size.toLong()); it[7] = protocol.toByte() }
        return of(src + dst + tail + body)
    }
}
