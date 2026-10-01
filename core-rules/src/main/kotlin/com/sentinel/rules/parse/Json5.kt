package com.sentinel.rules.parse

import kotlinx.serialization.json.JsonPrimitive

object Json5 {
    fun toJson(text: String): String {
        val tokens = mutableListOf<String>()
        var pos = 0
        fun fail(): Nothing = throw IllegalArgumentException("Invalid JSON5 at $pos")
        fun quoted(): String {
            val quote = text[pos++]
            val value = StringBuilder()
            while (pos < text.length) {
                val char = text[pos++]
                if (char == quote) return JsonPrimitive(value.toString()).toString()
                if (char == '\n' || char == '\r') fail()
                if (char != '\\') { value.append(char); continue }
                if (pos == text.length) fail()
                when (val escaped = text[pos++]) {
                    '\n' -> Unit
                    '\r' -> if (text.getOrNull(pos) == '\n') pos++
                    'n' -> value.append('\n')
                    'r' -> value.append('\r')
                    't' -> value.append('\t')
                    'b' -> value.append('\b')
                    'f' -> value.append('\u000c')
                    'v' -> value.append('\u000b')
                    '0' -> value.append('\u0000')
                    'u', 'x' -> {
                        val size = if (escaped == 'u') 4 else 2
                        if (pos + size > text.length) fail()
                        val code = text.substring(pos, pos + size).toIntOrNull(16) ?: fail()
                        value.append(code.toChar())
                        pos += size
                    }
                    else -> value.append(escaped)
                }
            }
            fail()
        }
        while (pos < text.length) {
            val char = text[pos]
            when {
                char.isWhitespace() -> pos++
                text.startsWith("//", pos) -> {
                    pos += 2
                    while (pos < text.length && text[pos] !in "\r\n") pos++
                }
                text.startsWith("/*", pos) -> {
                    val end = text.indexOf("*/", pos + 2)
                    if (end < 0) fail()
                    pos = end + 2
                }
                char == '"' || char == '\'' -> tokens += quoted()
                char in "{}[]:," -> { tokens += char.toString(); pos++ }
                else -> {
                    val start = pos
                    while (pos < text.length && !text[pos].isWhitespace() && text[pos] !in "{}[]:,\"'" &&
                        !text.startsWith("//", pos) && !text.startsWith("/*", pos)) pos++
                    if (start == pos) fail()
                    tokens += text.substring(start, pos)
                }
            }
        }
        return buildString {
            for ((i, token) in tokens.withIndex()) {
                if (token == "," && tokens.getOrNull(i + 1) in listOf("}", "]")) continue
                if (tokens.getOrNull(i + 1) == ":" && !token.startsWith('"')) {
                    require(token.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) { "Invalid JSON5 key" }
                    append(JsonPrimitive(token))
                } else append(token)
            }
        }
    }
}
