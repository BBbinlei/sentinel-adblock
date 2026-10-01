package com.sentinel.regression

import com.sentinel.rules.domain.DomainCompiler
import com.sentinel.rules.model.*
import com.sentinel.rules.parse.*
import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.Test

class SubscriptionHealthTest {
    private fun rawGkdRuleCount(text: String): Int {
        val root = Json.parseToJsonElement(Json5.toJson(text)).jsonObject
        return root["apps"]!!.jsonArray.sumOf { app ->
            app.jsonObject["groups"]!!.jsonArray.sumOf { group ->
                when (val rules = group.jsonObject["rules"]) {
                    is JsonArray -> rules.size
                    null -> 0
                    else -> 1
                }
            }
        }
    }
    @Test fun RR_04a_all_samples_parse() {
        val samples = Fixtures.subscriptions
        assertEquals(3, samples.size)
        for (file in samples) {
            val result = if (file.extension == "txt") Fixtures.domainRules(file)
                else GkdParser.parse(file.readText(), file.name)
            assertTrue(result.rules.isNotEmpty(), "No accepted rules: ${file.name}")
        }
    }
    @Test fun RR_04b_adguard_skipped_below_20_percent() {
        val rows = Fixtures.subscriptions.filter { it.extension == "txt" }.map { file ->
            val result = Fixtures.domainRules(file)
            val total = result.rules.size + result.skipped
            val ratio = result.skipped.toDouble() / total
            Triple(file.name, ratio, "${file.name}: rules=${result.rules.size}, skipped=${result.skipped}, total=$total, skip_rate=$ratio")
        }
        Fixtures.report("adguard-coverage.txt", rows.joinToString("\n") { it.third } + "\n")
        for ((_, ratio, row) in rows) assertTrue(ratio < 0.20, row)
    }
    @Test fun RR_04c_gkd_acceptance_report() {
        val rows = Fixtures.subscriptions.filter { it.extension != "txt" }.map { file ->
            val text = file.readText()
            val result = GkdParser.parse(text, file.name)
            val total = rawGkdRuleCount(text)
            val accepted = result.rules.map { it.id }.distinct().size
            assertEquals(total, accepted + result.skipped, "Every input rule accounted for")
            assertTrue(accepted > 0)
            "${file.name}: input_rules=$total, accepted_input_rules=$accepted, skipped=${result.skipped}, emitted_rules=${result.rules.size}, acceptance_rate=${accepted.toDouble() / total}"
        }
        Fixtures.report("gkd-coverage.txt", rows.joinToString("\n") + "\n")
    }
    @Test fun RR_04d_compiled_size_below_8_mb() {
        val rules = Fixtures.subscriptions.filter { it.extension == "txt" }.flatMap { Fixtures.domainRules(it).rules }
        val bytes = DomainCompiler.compile(rules)
        Fixtures.report("subscription-size.txt", "parsed_dns_rules=${rules.size}\nbytes=${bytes.size}\n")
        assertTrue(bytes.size < 8_000_000, "Compiled size ${bytes.size}")
    }
}
