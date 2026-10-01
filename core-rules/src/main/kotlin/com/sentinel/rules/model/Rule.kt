package com.sentinel.rules.model

import kotlinx.serialization.Serializable

@Serializable enum class RuleLevel { STANDARD, STRONG }
@Serializable enum class DomainTag { AD, AD_SDK, TRACKER, HTTPDNS }
@Serializable sealed interface RuleScope {
    @Serializable data object Global : RuleScope
    @Serializable data class App(val pkg: String) : RuleScope
    @Serializable data class Page(val pkg: String, val activity: String) : RuleScope
}
@Serializable enum class UiAction { CLICK, REWARDED_HANDLE, NOTIFY_AUTORENEW }
@Serializable enum class UiPhase { LAUNCH, ANYTIME }
@Serializable sealed interface Rule { val id: String; val source: String }
@Serializable data class DnsRule(override val id: String, val domain: String, val tag: DomainTag,
    val level: RuleLevel, override val source: String) : Rule
@Serializable data class UiRule(override val id: String, val scope: RuleScope, val selector: String,
    val action: UiAction, val phase: UiPhase, override val source: String) : Rule
@Serializable data class NotifyRule(override val id: String, val pkg: String?, val channelId: String?,
    val keywords: List<String>, override val source: String) : Rule
data class ParseResult<T : Rule>(val rules: List<T>, val skipped: Int)
