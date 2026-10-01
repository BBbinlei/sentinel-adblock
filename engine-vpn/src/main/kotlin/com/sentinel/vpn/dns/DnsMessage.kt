package com.sentinel.vpn.dns

import com.sentinel.vpn.packet.*
import java.io.ByteArrayOutputStream

data class DnsQuestion(val id: Int, val name: String, val qtype: Int)
object DnsMessage {
    private fun name(b: ByteArray, start: Int): Pair<String, Int>? {
        var p = start; var end = -1; var size = 1
        val seen = HashSet<Int>(); val labels = mutableListOf<String>()
        while (p in b.indices && seen.add(p)) {
            val n = b[p].toInt() and 255
            when {
                n == 0 -> return labels.joinToString(".") to if (end < 0) p + 1 else end
                n and 0xc0 == 0xc0 -> {
                    if (p + 1 >= b.size) return null
                    if (end < 0) end = p + 2
                    p = ((n and 63) shl 8) or (b[p + 1].toInt() and 255)
                }
                n > 63 || p + 1 + n > b.size -> return null
                else -> {
                    size += n + 1
                    if (size > 255) return null
                    if ((p + 1..p + n).any { b[it].toInt() and 255 !in 33..126 || b[it] == '.'.code.toByte() }) return null
                    labels += b.copyOfRange(p + 1, p + 1 + n).toString(Charsets.US_ASCII); p += n + 1
                }
            }
        }
        return null
    }
    fun parseQuestion(payload: ByteArray, off: Int, len: Int): DnsQuestion? {
        if (off < 0 || len < 12 || off > payload.size - len) return null
        val b = payload.copyOfRange(off, off + len)
        if (u16(b, 4) != 1) return null
        val (domain, end) = name(b, 12) ?: return null
        if (end + 4 > b.size || u16(b, end + 2) != 1) return null
        return DnsQuestion(u16(b, 0), domain.lowercase(java.util.Locale.ROOT), u16(b, end))
    }
    private fun response(query: ByteArray, failure: Boolean): ByteArray {
        val q = parseQuestion(query, 0, query.size)
        val header = ByteArray(12).also {
            if (query.size >= 2) put16(it, 0, u16(query, 0))
            val rd = if (query.size >= 4) u16(query, 2) and 0x0100 else 0
            put16(it, 2, 0x8080 or rd or if (failure) 2 else 0)
            put16(it, 4, if (q == null) 0 else 1)
        }
        if (q == null) return header
        val (domain, end) = requireNotNull(name(query, 12))
        val question = ByteArrayOutputStream().also { out ->
            if (domain.isNotEmpty()) domain.split('.').forEach { out.write(it.length); out.write(it.toByteArray(Charsets.US_ASCII)) }
            out.write(0); out.write(query.copyOfRange(end, end + 4))
        }.toByteArray()
        val count = if (failure) 0 else when (q.qtype) { 1 -> 4; 28 -> 16; else -> 0 }
        if (count == 0) return header + question
        put16(header, 6, 1)
        val answer = ByteArray(12 + count).also {
            it[0] = 0xc0.toByte(); it[1] = 12; put16(it, 2, q.qtype); put16(it, 4, 1)
            put32(it, 6, 60); put16(it, 10, count)
        }
        return header + question + answer
    }
    fun blockedResponse(query: ByteArray) = response(query, false)
    fun servFail(query: ByteArray) = response(query, true)
    fun withId(msg: ByteArray, id: Int) = msg.copyOf().also { if (it.size >= 2) put16(it, 0, id) }
    fun minTtl(response: ByteArray): Int? {
        if (response.size < 12 || u16(response, 2) and 0x820f != 0x8000) return null
        var p = 12
        repeat(u16(response, 4)) {
            p = (name(response, p) ?: return null).second + 4
            if (p > response.size) return null
        }
        var min: Long? = null
        repeat(u16(response, 6) + u16(response, 8) + u16(response, 10)) {
            p = (name(response, p) ?: return null).second
            if (p + 10 > response.size) return null
            if (u16(response, p) != 41) min = minOf(min ?: Long.MAX_VALUE, u32(response, p + 4))
            val len = u16(response, p + 8); p += 10 + len
            if (p > response.size) return null
        }
        return min?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
    }
}
