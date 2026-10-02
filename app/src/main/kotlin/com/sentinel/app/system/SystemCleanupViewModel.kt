package com.sentinel.app.system

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.system.ops.OpExecutor
import com.sentinel.system.ops.OpOutcome
import com.sentinel.system.ops.OpStatus
import com.sentinel.system.profile.ColorOsProfile
import com.sentinel.system.profile.OpKind
import com.sentinel.system.profile.ProfileOp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CleanupItem(val op: ProfileOp, val status: OpStatus, val canAuto: Boolean)

class SystemCleanupViewModel(
    private val profile: ColorOsProfile,
    private val executor: OpExecutor,
) : ViewModel() {
    private val statuses = MutableStateFlow<Map<String, OpStatus>>(emptyMap())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val state: StateFlow<List<CleanupItem>> = statuses.map { known ->
        profile.ops.map { op ->
            CleanupItem(op, known[op.id] ?: OpStatus.UNKNOWN, canAuto = op.verified && op.kind != OpKind.WIZARD)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
        profile.ops.map { CleanupItem(it, OpStatus.UNKNOWN, it.verified && it.kind != OpKind.WIZARD) })

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { statuses.value = profile.ops.associate { it.id to executor.status(it) } }
    }

    /** 一键应用：跳过可选项、卸载项、需要向导的项，以及尚未在真机核实的项。 */
    fun applyAll() {
        viewModelScope.launch {
            val ops = profile.ops.filter { it.verified && !it.optional && it.kind != OpKind.UNINSTALL && it.kind != OpKind.WIZARD }
            val result = executor.applyAll(ops)
            _message.value = summarize(result.values)
            statuses.value = profile.ops.associate { it.id to executor.status(it) }
        }
    }

    fun apply(id: String) {
        val op = profile.ops.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            _message.value = summarize(listOf(executor.apply(op)))
            statuses.value = profile.ops.associate { it.id to executor.status(it) }
        }
    }

    /** 需要用户在系统设置里手动操作的项，返回对应的设置页 Intent。 */
    fun openSettings(id: String): Intent? {
        val intent = profile.ops.firstOrNull { it.id == id }?.intent ?: return null
        val result = Intent(intent.action ?: Intent.ACTION_MAIN)
        if (intent.pkg != null && intent.cls != null) result.setClassName(intent.pkg!!, intent.cls!!)
        else if (intent.pkg != null) result.setPackage(intent.pkg)
        return result.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun undo(logId: Long) {
        viewModelScope.launch {
            _message.value = if (executor.undo(logId)) "已撤销" else "撤销失败"
            statuses.value = profile.ops.associate { it.id to executor.status(it) }
        }
    }

    fun undoAll() {
        viewModelScope.launch {
            _message.value = "已撤销 ${executor.undoAll()} 项"
            statuses.value = profile.ops.associate { it.id to executor.status(it) }
        }
    }

    private fun summarize(outcomes: Collection<OpOutcome>): String {
        val applied = outcomes.count { it is OpOutcome.Applied }
        val failed = outcomes.count { it is OpOutcome.Failed }
        return when {
            failed > 0 -> "完成 $applied 项，失败 $failed 项"
            applied > 0 -> "已应用 $applied 项"
            else -> "没有需要应用的项"
        }
    }
}
