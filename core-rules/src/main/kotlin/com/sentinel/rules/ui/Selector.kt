package com.sentinel.rules.ui

import java.util.IdentityHashMap

class SelectorSyntaxException(msg: String) : Exception(msg)

class Selector private constructor(private val segments: List<Segment>, private val direct: List<Boolean>) {
    internal val isSingleAttribute: Boolean get() = segments.size == 1 && segments[0].className == null

    fun findFirst(root: NodeView): NodeView? = find(root, firstOnly = true).firstOrNull()
    fun findAll(root: NodeView): List<NodeView> = find(root, firstOnly = false)

    private fun find(root: NodeView, firstOnly: Boolean): List<NodeView> {
        val parents = IdentityHashMap<NodeView, NodeView?>()
        val stack = java.util.ArrayDeque<Pair<NodeView, NodeView?>>()
        stack.add(root to null)
        val found = mutableListOf<NodeView>()
        fun matchesChain(node: NodeView, index: Int): Boolean {
            if (!segments[index].matches(node)) return false
            if (index == 0) return true
            var parent = parents[node]
            while (parent != null) {
                if (matchesChain(parent, index - 1)) return true
                if (direct[index - 1]) break
                parent = parents[parent]
            }
            return false
        }
        while (stack.isNotEmpty()) {
            val (node, parent) = stack.removeLast()
            if (parents.containsKey(node)) continue
            parents[node] = parent
            if (matchesChain(node, segments.lastIndex)) {
                found += node
                if (firstOnly) return found
            }
            for (child in node.children.asReversed()) stack.add(child to node)
        }
        return found
    }

    private data class Segment(val className: String?, val predicates: List<(NodeView) -> Boolean>) {
        fun matches(node: NodeView): Boolean = (className == null || className == "*" ||
            node.className == className || node.className?.substringAfterLast('.') == className) &&
            predicates.all { it(node) }
    }

    private class Parser(private val text: String) {
        private var pos = 0
        private fun fail(): Nothing = throw SelectorSyntaxException("Invalid selector at $pos: $text")
        private fun peek(): Char? = text.getOrNull(pos)
        private fun whitespace(): Boolean {
            val start = pos
            while (peek()?.isWhitespace() == true) pos++
            return pos > start
        }
        private fun identifier(): String {
            val start = pos
            while (peek()?.let { it.isLetterOrDigit() || it in "._" || (it == '$' && text.getOrNull(pos + 1) != '=') } == true) pos++
            if (pos == start) fail()
            return text.substring(start, pos)
        }
        private fun value(): Pair<String, Boolean> {
            if (peek() != '"') {
                val value = identifier()
                if (value !in listOf("true", "false")) fail()
                return value to false
            }
            pos++
            val value = StringBuilder()
            while (true) {
                val char = peek() ?: fail()
                pos++
                if (char == '"') return value.toString() to true
                if (char == '\\') {
                    val next = peek() ?: fail()
                    pos++
                    if (next != '"' && next != '\\') value.append('\\')
                    value.append(next)
                } else value.append(char)
            }
        }
        private fun predicate(): (NodeView) -> Boolean {
            pos++ // [
            whitespace()
            val attr = identifier()
            if (attr !in listOf("text", "desc", "vid", "id", "clickable", "checked")) fail()
            whitespace()
            val op = listOf("^=", "$=", "*=", "~=", "=").firstOrNull { text.startsWith(it, pos) } ?: fail()
            pos += op.length
            whitespace()
            val (value, quoted) = value()
            if (attr in listOf("clickable", "checked")) {
                if (quoted || op != "=") fail()
            } else if (!quoted) fail()
            whitespace()
            if (peek() != ']') fail()
            pos++
            val regex = if (op == "~=") try { Regex(value) } catch (_: IllegalArgumentException) { fail() } else null
            return { node ->
                val actual = when (attr) {
                    "text" -> node.text
                    "desc" -> node.desc
                    "vid" -> node.viewId?.takeIf { ":id/" in it }?.substringAfter(":id/")
                    "id" -> node.viewId
                    "clickable" -> node.clickable.toString()
                    else -> node.checked.toString()
                }
                actual != null && when (op) {
                    "=" -> actual == value
                    "^=" -> actual.startsWith(value)
                    "$=" -> actual.endsWith(value)
                    "*=" -> value in actual
                    else -> regex!!.containsMatchIn(actual)
                }
            }
        }
        private fun segment(): Segment {
            val className = when (peek()) {
                '*' -> { pos++; "*" }
                '[' -> null
                else -> identifier()
            }
            val predicates = mutableListOf<(NodeView) -> Boolean>()
            while (peek() == '[') predicates += predicate()
            return Segment(className, predicates)
        }
        fun parse(): Selector {
            whitespace()
            val segments = mutableListOf(segment())
            val direct = mutableListOf<Boolean>()
            while (true) {
                val spaced = whitespace()
                if (pos == text.length) return Selector(segments, direct)
                val child = peek() == '>'
                if (child) { pos++; whitespace() } else if (!spaced) fail()
                direct += child
                segments += segment()
            }
        }
    }

    companion object {
        fun parse(text: String): Selector = Parser(text).parse()
    }
}
