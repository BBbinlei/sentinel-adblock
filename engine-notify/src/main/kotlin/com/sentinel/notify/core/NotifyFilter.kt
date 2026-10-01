package com.sentinel.notify.core

import com.sentinel.data.db.EffectiveConfig
import com.sentinel.data.db.ProtectLevel
import com.sentinel.rules.model.NotifyRule
import com.sentinel.rules.notify.NotifyMatcher

data class PostedNotification(val pkg: String, val channelId: String?, val title: String?,
    val text: String?, val ongoing: Boolean, val key: String)

object NotifyFilter {
    fun decide(n: PostedNotification, selfPkg: String, cfg: EffectiveConfig,
        rules: List<NotifyRule>, disabled: Set<String>): NotifyRule? {
        if (n.pkg == selfPkg || n.ongoing || cfg.level == ProtectLevel.OFF || !cfg.notify) return null
        return rules.firstOrNull { it.id !in disabled && NotifyMatcher.isValid(it) &&
            NotifyMatcher.matches(it, n.pkg, n.channelId, n.title, n.text) }
    }
}
