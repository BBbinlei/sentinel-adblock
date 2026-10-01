package com.sentinel.notify.core

import com.sentinel.data.db.EffectiveConfig
import com.sentinel.data.db.ProtectLevel
import com.sentinel.rules.model.NotifyRule

interface NotifyState {
    fun cfg(pkg: String): EffectiveConfig
    fun disabled(pkg: String): Set<String>
    fun rules(): List<NotifyRule>
}

sealed interface NotifyAction {
    data class Cancel(val key: String, val pkg: String, val ruleId: String, val title: String?) : NotifyAction
    data class AskLearn(val rule: NotifyRule) : NotifyAction
}

class NotifyEngine(private val selfPkg: String, private val state: NotifyState, private val learner: DismissLearner) {
    fun onPosted(n: PostedNotification): NotifyAction? =
        NotifyFilter.decide(n, selfPkg, state.cfg(n.pkg), state.rules(), state.disabled(n.pkg))?.let {
            NotifyAction.Cancel(n.key, n.pkg, it.id, n.title)
        }

    fun onUserDismissed(pkg: String, channelId: String?): NotifyAction? {
        if (pkg == selfPkg) return null
        val cfg = state.cfg(pkg)
        if (cfg.level == ProtectLevel.OFF || !cfg.notify) return null
        val id = "learn:notify:$pkg:$channelId"
        if (id in state.disabled(pkg) || state.rules().any { it.id == id }) return null
        return learner.onUserDismissed(pkg, channelId)?.let(NotifyAction::AskLearn)
    }

    fun onLearnRejected(pkg: String, channelId: String?) = learner.onRejected(pkg, channelId)
}
