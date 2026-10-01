package com.sentinel.rules.parse

import com.sentinel.rules.model.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class AdGuardParserTest {
    @Test fun `UT-CR-1-03 supported domain syntax only`() {
        val result = AdGuardParser.parse("||ads.x.com^\n||y.com^\$third-party\n@@||ok.com^\n##.banner\n! c", "test", RuleLevel.STRONG, DomainTag.AD)
        assertEquals(listOf("ads.x.com"), result.rules.map { it.domain })
        assertEquals(3, result.skipped)
    }
}
