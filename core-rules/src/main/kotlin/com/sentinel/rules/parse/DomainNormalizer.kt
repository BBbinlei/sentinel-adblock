package com.sentinel.rules.parse

object DomainNormalizer {
    private val label = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
    fun normalize(raw: String): String? {
        val domain = raw.trim().lowercase().trimEnd('.')
        if (domain.length > 253 || '.' !in domain || domain.all { it.isDigit() || it == '.' }) return null
        return domain.takeIf { it.split('.').all(label::matches) }
    }
}
