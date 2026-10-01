package com.sentinel.rules.parse

import com.sentinel.rules.model.*
import kotlinx.serialization.json.Json

object JsonRuleParser {
    fun parse(json: String): List<Rule> = Json.decodeFromString<List<Rule>>(json).map { rule ->
        if (rule is DnsRule) {
            val domain = requireNotNull(DomainNormalizer.normalize(rule.domain)) { "Invalid DNS rule" }
            rule.copy(id = "dns:$domain", domain = domain)
        } else rule
    }
    fun encode(rules: List<Rule>): String = Json.encodeToString(rules)
}
