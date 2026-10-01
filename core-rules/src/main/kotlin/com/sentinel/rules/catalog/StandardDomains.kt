package com.sentinel.rules.catalog

import com.sentinel.rules.model.*
import com.sentinel.rules.parse.DomainNormalizer

object StandardDomains {
    fun rules(): List<DnsRule> = requireNotNull(javaClass.getResourceAsStream("/standard_domains.txt"))
        .bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val (raw, tag) = line.split('\t')
                val domain = requireNotNull(DomainNormalizer.normalize(raw))
                DnsRule("dns:$domain", domain, DomainTag.valueOf(tag), RuleLevel.STANDARD, "builtin")
            }.toList()
        }
}
