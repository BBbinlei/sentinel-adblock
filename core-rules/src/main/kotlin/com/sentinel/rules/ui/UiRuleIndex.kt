package com.sentinel.rules.ui

import com.sentinel.rules.model.*

data class CompiledUiRule(val rule: UiRule, val selector: Selector)
class UiRuleIndex private constructor(
    private val byScope: Map<RuleScope, List<CompiledUiRule>>, val invalidCount: Int
) {
    fun lookup(pkg: String, activity: String?): List<CompiledUiRule> =
        (if (activity == null) emptyList() else byScope[RuleScope.Page(pkg, activity)].orEmpty()) +
            byScope[RuleScope.App(pkg)].orEmpty() + byScope[RuleScope.Global].orEmpty()
    companion object {
        fun build(rules: List<UiRule>): UiRuleIndex {
            var invalid = 0
            val compiled = rules.mapNotNull { rule ->
                try { CompiledUiRule(rule, Selector.parse(rule.selector)) }
                catch (_: SelectorSyntaxException) { invalid++; null }
            }
            return UiRuleIndex(compiled.groupBy { it.rule.scope }, invalid)
        }
    }
}
