package com.sentinel.system.drift

import android.util.Log
import com.sentinel.data.db.EngineId
import com.sentinel.data.db.EngineState
import com.sentinel.data.repo.EngineStatusRepository
import com.sentinel.system.shell.ShizukuState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SystemStatusReporter(
    private val status: EngineStatusRepository, private val shizukuState: StateFlow<ShizukuState>,
    private val driftedCount: StateFlow<Int>, private val pendingCount: StateFlow<Int>,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            combine(shizukuState, driftedCount, pendingCount) { s, d, p -> Triple(s, d, p) }
                .distinctUntilChanged()
                .collect { (s, d, p) ->
                    try {
                        if (s != ShizukuState.READY) status.report(EngineId.SYSTEM, EngineState.DEGRADED, "Shizuku 未激活（不影响已生效项）")
                        else if (d + p > 0) status.report(EngineId.SYSTEM, EngineState.DEGRADED, "${d + p} 项待处理")
                        else status.report(EngineId.SYSTEM, EngineState.RUNNING)
                    } catch (e: Exception) { currentCoroutineContext().ensureActive(); Log.w("SentinelSystem", "状态上报失败", e) }
                }
        }
    }
}
