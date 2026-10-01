package com.sentinel.regression

import com.sentinel.rules.catalog.*
import com.sentinel.rules.domain.*
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.*
import org.junit.Test

class TopDomainsFalsePositiveTest {
    private val topDomains get() = File(Fixtures.root, "top-domains-cn.txt").readLines()
        .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
    private val standard get() = StandardDomains.rules() + HttpDnsCatalog.rules()
    @Test fun RR_01a_standard_no_false_positives() {
        val domains = topDomains
        assertEquals(987, domains.size, "Fixed fixture count documented in its header")
        val matcher = DomainMatcher(ByteBuffer.wrap(DomainCompiler.compile(standard)))
        val hits = domains.mapNotNull { domain -> matcher.lookup(domain)?.let { "$domain -> ${it.ruleDomain}" } }
        Fixtures.report("standard-hits.txt", "domains=${domains.size}\nhits=${hits.size}\n" + hits.joinToString("\n"))
        assertEquals(emptyList(), hits, "STANDARD hits:\n" + hits.joinToString("\n"))
    }
    @Test fun RR_01b_strong_report_only() {
        val subscriptions = Fixtures.subscriptions.filter { it.extension == "txt" }.flatMap { Fixtures.domainRules(it).rules }
        val matcher = DomainMatcher(ByteBuffer.wrap(DomainCompiler.compile(standard + subscriptions)))
        val hits = topDomains.mapNotNull { domain -> matcher.lookup(domain)?.let { "$domain -> ${it.ruleDomain}" } }
        Fixtures.report("strong-hits.txt", "domains=${topDomains.size}\nhits=${hits.size}\n" + hits.joinToString("\n"))
    }
}
