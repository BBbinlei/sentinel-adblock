package com.sentinel.rules.notify

import com.sentinel.rules.model.NotifyRule

object NotifyMatcher {
    fun isValid(rule: NotifyRule): Boolean = rule.pkg != null || rule.channelId != null || rule.keywords.isNotEmpty()
    fun matches(rule: NotifyRule, pkg: String, channelId: String?, title: String?, text: String?): Boolean =
        isValid(rule) && (rule.pkg == null || rule.pkg == pkg) &&
            (rule.channelId == null || rule.channelId == channelId) &&
            (rule.keywords.isEmpty() || rule.keywords.any { keyword ->
                title?.contains(keyword) == true || text?.contains(keyword) == true
            })
}
