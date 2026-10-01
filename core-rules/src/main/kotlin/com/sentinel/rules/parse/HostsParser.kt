package com.sentinel.rules.parse

import com.sentinel.rules.model.*

object HostsParser {
    fun parse(text: String, source: String, level: RuleLevel, tag: DomainTag): ParseResult<DnsRule> {
        val rules = mutableListOf<DnsRule>()
        var skipped = 0
        for (line in text.lineSequence()) {
            val parts = line.substringBefore('#').trim().split(Regex("\\s+"))
            if (parts == listOf("")) continue
            val domains = if (parts.first() in setOf("0.0.0.0", "127.0.0.1")) parts.drop(1)
                          else if (parts.size == 1) parts else emptyList()
            if (domains.isEmpty()) skipped++
            for (raw in domains) {
                if (raw.lowercase().trimEnd('.') == "localhost") continue
                val domain = DomainNormalizer.normalize(raw)
                if (domain == null) skipped++ else rules += DnsRule("dns:$domain", domain, tag, level, source)
            }
        }
        return ParseResult(rules, skipped)
    }
}
