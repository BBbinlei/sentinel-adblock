package com.sentinel.system.drift

import com.sentinel.data.repo.OpLogRepository
import com.sentinel.system.ops.*
import com.sentinel.system.profile.ColorOsProfile
import com.sentinel.system.shell.ShizukuState
import kotlinx.coroutines.flow.*

data class DriftReport(val drifted: List<String>, val checked: Int)

class DriftInspector(
    private val executor: OpExecutor, private val profile: ColorOsProfile?,
    private val log: OpLogRepository, private val shizukuState: StateFlow<ShizukuState>,
) {
    private val driftedFlow = MutableStateFlow(0)
    /** 最近一次巡检发现的漂移项数量，供状态上报使用。 */
    val driftedCount: StateFlow<Int> = driftedFlow.asStateFlow()

    /** Shizuku 非 READY 时返回 null 且不发任何命令。 */
    suspend fun inspect(): DriftReport? {
        if (shizukuState.value != ShizukuState.READY) return null
        val ops = profile?.ops.orEmpty()
        val appliedIds = log.observeAll().first().filter { it.success && !it.undone }.map { it.opId }.toSet()
        val drifted = mutableListOf<String>()
        var checked = 0
        for (op in ops) {
            if (op.id !in appliedIds) continue
            when (executor.status(op)) {
                OpStatus.APPLIED -> checked++
                OpStatus.NOT_APPLIED -> { checked++; drifted += op.id }
                OpStatus.UNKNOWN -> Unit
            }
        }
        driftedFlow.value = drifted.size
        return DriftReport(drifted, checked)
    }

    suspend fun reapply(opIds: List<String>): Map<String, OpOutcome> {
        if (shizukuState.value != ShizukuState.READY) return emptyMap()
        val wanted = opIds.toSet()
        val result = executor.applyAll(profile?.ops.orEmpty().filter { it.id in wanted })
        if (result.values.any { it == OpOutcome.Applied || it == OpOutcome.AlreadyApplied }) {
            driftedFlow.value = (driftedFlow.value - result.count { it.value == OpOutcome.Applied || it.value == OpOutcome.AlreadyApplied }).coerceAtLeast(0)
        }
        return result
    }
}
