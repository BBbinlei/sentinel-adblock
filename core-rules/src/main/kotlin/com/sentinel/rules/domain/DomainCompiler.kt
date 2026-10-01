package com.sentinel.rules.domain

import com.sentinel.rules.model.*
import com.sentinel.rules.parse.DomainNormalizer
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DomainCompiler {
    fun compile(rules: Collection<DnsRule>): ByteArray {
        val unique = sortedMapOf<String, DnsRule>()
        for (rule in rules) {
            val domain = requireNotNull(DomainNormalizer.normalize(rule.domain)) { "Invalid domain: ${rule.domain}" }
            if (domain !in unique || rule.level == RuleLevel.STANDARD) unique[domain] = rule.copy(domain = domain)
        }
        val total = 10L + unique.size * 4L + unique.keys.sumOf { 4L + it.length }
        require(total <= Int.MAX_VALUE) { "Domain file too large" }
        val buffer = ByteBuffer.allocate(total.toInt()).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("SNTL".toByteArray(Charsets.US_ASCII)).putShort(1).putInt(unique.size)
        var offset = 0
        for (domain in unique.keys) {
            buffer.putInt(offset)
            offset += 4 + domain.length
        }
        for ((domain, rule) in unique) {
            val bytes = domain.toByteArray(Charsets.UTF_8)
            buffer.put(rule.tag.ordinal.toByte()).put(rule.level.ordinal.toByte()).putShort(bytes.size.toShort()).put(bytes)
        }
        return buffer.array()
    }
}
