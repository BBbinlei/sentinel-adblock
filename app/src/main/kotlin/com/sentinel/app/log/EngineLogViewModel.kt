package com.sentinel.app.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.data.db.EngineId
import com.sentinel.data.db.EventEntity
import com.sentinel.data.repo.EventRepository
import com.sentinel.rules.model.EventKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

private const val LOG_LIMIT = 200

/** 每个引擎在日志页里展示哪些事件。 */
fun eventKindsOf(engine: EngineId): Set<EventKind> = when (engine) {
    EngineId.VPN -> setOf(EventKind.DNS_BLOCKED, EventKind.HTTPDNS_REJECTED, EventKind.WOULD_BLOCK)
    EngineId.A11Y -> setOf(EventKind.SPLASH_SKIPPED, EventKind.POPUP_CLOSED, EventKind.REWARDED_SILENCED,
        EventKind.JUMP_REVERTED, EventKind.AUTO_RENEW_WARNED)
    EngineId.NOTIFY -> setOf(EventKind.NOTIFICATION_CANCELLED)
    EngineId.SYSTEM -> setOf(EventKind.SYSTEM_OP)
}

class EngineLogViewModel(val engine: EngineId, events: EventRepository) : ViewModel() {
    val state: StateFlow<List<EventEntity>> = events.observeRecent(eventKindsOf(engine), LOG_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
