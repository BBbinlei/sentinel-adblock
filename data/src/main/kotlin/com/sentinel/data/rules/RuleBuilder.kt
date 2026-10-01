package com.sentinel.data.rules

import com.sentinel.data.db.*
import com.sentinel.rules.catalog.*
import com.sentinel.rules.domain.DomainCompiler
import com.sentinel.rules.model.*
import com.sentinel.rules.parse.*
import com.sentinel.rules.ui.BuiltInUiRules

object RuleBuilder {
    internal fun parse(sub: SubscriptionEntity, text: String): ParseResult<Rule> = when (sub.format) {
        SubscriptionFormat.HOSTS -> HostsParser.parse(text, sub.name, sub.level, sub.tag).let { ParseResult(it.rules, it.skipped) }
        SubscriptionFormat.ADGUARD -> AdGuardParser.parse(text, sub.name, sub.level, sub.tag).let { ParseResult(it.rules, it.skipped) }
        SubscriptionFormat.GKD -> GkdParser.parse(text, sub.name).let { ParseResult(it.rules, it.skipped) }
        SubscriptionFormat.SENTINEL_JSON -> ParseResult(JsonRuleParser.parse(text).map {
            if (it is DnsRule) it.copy(level = sub.level, tag = sub.tag, source = sub.name) else it
        }, 0)
    }
    fun build(subs: List<Pair<SubscriptionEntity, String>>, userRules: List<Rule>): BuiltRules {
        val standard = StandardDomains.rules()
        val httpDns = HttpDnsCatalog.rules()
        val all = mutableListOf<Rule>().apply { addAll(standard); addAll(httpDns); addAll(BuiltInUiRules.all) }
        val stats = mutableMapOf("builtin:domains" to standard.size, "builtin:httpdns" to httpDns.size,
            "builtin:ui" to BuiltInUiRules.all.size, "user" to userRules.size)
        var skipped = 0
        for ((sub, text) in subs.filter { it.first.enabled }) {
            val parsed = parse(sub, text)
            all.addAll(parsed.rules); skipped += parsed.skipped
            stats["subscription:${sub.id}"] = parsed.rules.size
            stats["subscription:${sub.id}:skipped"] = parsed.skipped
        }
        all.addAll(userRules)
        val dns = all.filterIsInstance<DnsRule>()
        val ui = all.filterIsInstance<UiRule>().distinctBy { it.id to it.scope }
        val notify = all.filterIsInstance<NotifyRule>().distinctBy { it.id }
        val domains = dns.map { requireNotNull(DomainNormalizer.normalize(it.domain)) }.toSet().size
        stats.putAll(mapOf("skipped" to skipped, "duplicates" to (dns.size - domains + all.filterIsInstance<UiRule>().size - ui.size + all.filterIsInstance<NotifyRule>().size - notify.size),
            "domains" to domains, "ui" to ui.size, "notify" to notify.size))
        return BuiltRules(DomainCompiler.compile(dns), JsonRuleParser.encode(ui), JsonRuleParser.encode(notify), stats)
    }
}
