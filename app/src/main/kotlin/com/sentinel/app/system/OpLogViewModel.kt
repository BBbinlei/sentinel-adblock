package com.sentinel.app.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.data.db.OpLogEntity
import com.sentinel.data.repo.OpLogRepository
import com.sentinel.system.ops.OpExecutor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OpLogViewModel(log: OpLogRepository, private val executor: OpExecutor) : ViewModel() {
    val state: StateFlow<List<OpLogEntity>> = log.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun undo(id: Long) { viewModelScope.launch { executor.undo(id) } }
}
