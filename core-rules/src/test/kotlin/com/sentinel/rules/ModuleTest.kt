package com.sentinel.rules

import com.sentinel.rules.model.*
import com.sentinel.rules.parse.*
import com.sentinel.rules.domain.*
import com.sentinel.rules.ui.*
import java.io.File
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.test.*
import org.junit.jupiter.api.Test

class ModuleTest {
    @Test fun MT_CR_01_parse_compile_match_end_to_end() {
        val hosts = HostsParser.parse("0.0.0.0 ads.example.com\n127.0.0.1 tracker.example.net", "hosts", RuleLevel.STANDARD, DomainTag.AD)
        val adguard = AdGuardParser.parse("||sdk.example.org^", "adguard", RuleLevel.STRONG, DomainTag.AD_SDK)
        val dns = hosts.rules + adguard.rules
        val matcher = DomainMatcher(ByteBuffer.wrap(DomainCompiler.compile(dns)))
        for (rule in dns) assertEquals(rule.domain, matcher.lookup("sub.${rule.domain}")?.ruleDomain)
        val gkd = GkdParser.parse("""{apps:[{id:'pkg',groups:[{key:0,rules:[
            {activityIds:['Main'],matches:'[text="关闭"]'},
            {matches:'[vid="skip"]'},
        ]}]}]}""", "gkd")
        assertEquals(0, gkd.skipped)
        val index = UiRuleIndex.build(gkd.rules)
        assertEquals(0, index.invalidCount)
        val snapshot = SnapshotNode(children = listOf(SnapshotNode(text = "关闭"), SnapshotNode(viewId = "pkg:id/skip")))
        assertEquals(2, index.lookup("pkg", "Main").size)
        for (compiled in index.lookup("pkg", "Main")) assertNotNull(compiled.selector.findFirst(snapshot))
    }
    @Test fun MT_CR_02_size_and_lookup_performance() {
        val random = Random(42)
        val domains = linkedSetOf<String>()
        while (domains.size < 200_000) domains += buildString {
            repeat(12) { append(('a'.code + random.nextInt(26)).toChar()) }
            append(".test")
        }
        val names = domains.toList()
        val rules = names.map { DnsRule("dns:$it", it, DomainTag.AD, RuleLevel.STRONG, "benchmark") }
        val bytes = DomainCompiler.compile(rules)
        assertTrue(bytes.size < 8_000_000, "Binary size ${bytes.size}")
        val matcher = DomainMatcher(ByteBuffer.wrap(bytes))
        assertEquals(200_000, matcher.size)
        val queries = List(100_000) { i ->
            if (i % 5 == 0) "missing-$i.test" else "sub.${names[random.nextInt(names.size)]}"
        }
        repeat(20_000) { matcher.lookup(queries[it]) }
        var hits = 0
        val start = System.nanoTime()
        for (query in queries) if (matcher.lookup(query) != null) hits++
        val elapsed = System.nanoTime() - start
        val averageUs = elapsed / queries.size / 1000.0
        val report = File("build/reports/module-metrics.txt")
        report.parentFile.mkdirs()
        report.writeText("domains=${matcher.size}\nbytes=${bytes.size}\nqueries=${queries.size}\nhits=$hits\naverage_us=$averageUs\nseed=42\n")
        assertEquals(80_000, hits)
        assertTrue(averageUs < 5.0, "Average lookup $averageUs µs")
    }
    @Test fun MT_CR_03_no_android_imports() {
        val source = listOf(File("src/main"), File("core-rules/src/main")).first { it.isDirectory }
        val files = source.walkTopDown().filter { it.isFile && it.extension in listOf("kt", "java") }.toList()
        assertTrue(files.isNotEmpty())
        val imports = Regex("""(?m)^\s*import\s+android\.""")
        assertEquals(emptyList(), files.filter { imports.containsMatchIn(it.readText()) }.map { it.path })
    }
}
