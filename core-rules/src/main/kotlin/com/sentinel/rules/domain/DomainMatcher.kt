package com.sentinel.rules.domain

import com.sentinel.rules.model.*
import com.sentinel.rules.parse.DomainNormalizer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

data class DomainHit(val ruleDomain: String, val tag: DomainTag, val level: RuleLevel) {
    val ruleId get() = "dns:$ruleDomain"
}

class DomainMatcher(buffer: ByteBuffer) {
    private val data = buffer.slice().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
    init { require(valid(data)) { "Invalid domain binary" } }
    val size: Int = data.getInt(6)
    private val entries = 10 + size * 4

    fun lookup(domain: String): DomainHit? {
        var suffix = DomainNormalizer.normalize(domain) ?: return null
        if (NeverBlockList.contains(suffix)) return null
        while (true) {
            val offset = search(suffix)
            if (offset >= 0) return DomainHit(suffix, DomainTag.entries[data.get(offset).toInt()],
                RuleLevel.entries[data.get(offset + 1).toInt()])
            val dot = suffix.indexOf('.')
            if (dot < 0 || suffix.indexOf('.', dot + 1) < 0) return null
            suffix = suffix.substring(dot + 1)
        }
    }

    private fun search(domain: String): Int {
        var low = 0
        var high = size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val offset = entries + data.getInt(10 + mid * 4)
            val length = data.getShort(offset + 2).toInt() and 0xffff
            var cmp = 0
            for (i in 0 until minOf(length, domain.length)) {
                cmp = (data.get(offset + 4 + i).toInt() and 0xff) - domain[i].code
                if (cmp != 0) break
            }
            if (cmp == 0) cmp = length - domain.length
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> return offset
            }
        }
        return -1
    }

    companion object {
        fun load(file: File): DomainMatcher =
            FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
                require(channel.size() in 10..Int.MAX_VALUE.toLong()) { "Invalid domain file size" }
                DomainMatcher(channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()))
            }
        fun verify(bytes: ByteArray): Boolean = valid(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))

        private fun valid(data: ByteBuffer): Boolean {
            if (data.limit() < 10 || (0..3).any { data.get(it) != "SNTL"[it].code.toByte() } ||
                data.getShort(4).toInt() != 1) return false
            val count = data.getInt(6)
            if (count < 0 || 10L + count * 4L > data.limit()) return false
            val base = 10 + count * 4
            var expected = 0
            var previous = ""
            for (i in 0 until count) {
                val relative = data.getInt(10 + i * 4)
                if (relative != expected || base.toLong() + relative + 4 > data.limit()) return false
                val offset = base + relative
                if (data.get(offset).toInt() !in DomainTag.entries.indices ||
                    data.get(offset + 1).toInt() !in RuleLevel.entries.indices) return false
                val length = data.getShort(offset + 2).toInt() and 0xffff
                if (base.toLong() + relative + 4 + length > data.limit()) return false
                val bytes = ByteArray(length) { data.get(offset + 4 + it) }
                val domain = bytes.toString(Charsets.UTF_8)
                if (DomainNormalizer.normalize(domain) != domain || domain <= previous) return false
                previous = domain
                expected += 4 + length
            }
            return base + expected == data.limit()
        }
    }
}
