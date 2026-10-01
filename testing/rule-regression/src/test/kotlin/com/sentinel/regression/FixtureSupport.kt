package com.sentinel.regression

import com.sentinel.rules.model.*
import com.sentinel.rules.parse.*
import java.io.File

internal object Fixtures {
    val root: File = listOf(File("fixtures"), File("testing/rule-regression/fixtures"))
        .first { File(it, "top-domains-cn.txt").isFile }
    val subscriptions: List<File> = File(root, "subscriptions").listFiles()!!
        .filter { it.extension in listOf("txt", "json", "json5") }.sortedBy { it.name }
    fun domainRules(file: File): ParseResult<DnsRule> =
        AdGuardParser.parse(file.readText(), file.name, RuleLevel.STRONG, DomainTag.AD)
    fun report(name: String, text: String) {
        val file = File(root.parentFile, "build/reports/$name")
        requireNotNull(file.parentFile).mkdirs()
        file.writeText(text)
    }
}
