package com.sentinel.notify.core

import com.sentinel.data.db.EffectiveConfig
import com.sentinel.rules.model.NotifyRule

interface NotifyState {
    fun cfg(pkg: String): EffectiveConfig
    fun disabled(pkg: String): Set<String>
    fun rules(): List<NotifyRule>
}
