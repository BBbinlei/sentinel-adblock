package com.sentinel.rules.parse

import com.sentinel.rules.model.*
import com.sentinel.rules.ui.Selector
import com.sentinel.rules.ui.SelectorSyntaxException
import kotlinx.serialization.json.*

object GkdParser {
    private fun strings(value: JsonElement?): List<String>? = when (value) {
        is JsonPrimitive -> if (value.isString) listOf(value.content) else null
        is JsonArray -> value.map { (it as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null }
        else -> null
    }
    fun parse(json: String, source: String): ParseResult<UiRule> {
        val root = Json.parseToJsonElement(Json5.toJson(json)).jsonObject
        val rules = mutableListOf<UiRule>()
        var skipped = 0
        for (appElement in root["apps"] as? JsonArray ?: emptyList()) {
            val app = appElement.jsonObject
            val pkg = app["id"]?.jsonPrimitive?.content ?: continue
            for (groupElement in app["groups"] as? JsonArray ?: emptyList()) {
                val group = groupElement.jsonObject
                val key = group["key"]?.jsonPrimitive?.content ?: continue
                // GKD shorthand strings carry the group's activity scope.
                val definitions = when (val value = group["rules"]) {
                    is JsonArray -> value
                    null -> emptyList()
                    else -> listOf(value)
                }
                for ((index, definition) in definitions.withIndex()) {
                    val rule = definition as? JsonObject ?: buildJsonObject { put("matches", definition) }
                    val matches = strings(rule["matches"])
                    val selectors = matches?.map { it.trim().removePrefix("@").trim() }
                    val supported = !selectors.isNullOrEmpty() && selectors.all { selector ->
                        try { Selector.parse(selector).isSingleAttribute } catch (_: SelectorSyntaxException) { false }
                    }
                    if (!supported) { skipped++; continue }
                    val activitiesValue = rule["activityIds"] ?: group["activityIds"]
                    val activities = if (activitiesValue == null) emptyList() else strings(activitiesValue)
                    if (activities == null) { skipped++; continue }
                    val scopes = if (activities.isEmpty()) listOf(RuleScope.App(pkg))
                        else activities.map { RuleScope.Page(pkg, it) }
                    // Attribute-only matches arrays are a conjunction on the same node.
                    val selector = selectors!!.joinToString("")
                    for (scope in scopes) rules += UiRule("gkd:$pkg:$key:$index", scope, selector,
                        UiAction.CLICK, UiPhase.ANYTIME, source)
                }
            }
        }
        return ParseResult(rules, skipped)
    }
}
