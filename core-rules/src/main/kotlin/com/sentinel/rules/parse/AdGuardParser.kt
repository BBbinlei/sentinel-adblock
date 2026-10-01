package com.sentinel.rules.parse

import com.sentinel.rules.model.*

object AdGuardParser {
    fun parse(text: String, source: String, level: RuleLevel, tag: DomainTag): ParseResult<DnsRule> {
        val rules = mutableListOf<DnsRule>()
        var skipped = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("!")) continue
            val domain = if (line.startsWith("||") && line.endsWith("^"))
                DomainNormalizer.normalize(line.substring(2, line.length - 1)) else null
            if (domain == null) skipped++ else rules += DnsRule("dns:$domain", domain, tag, level, source)
        }
        return ParseResult(rules, skipped)
    }
}
