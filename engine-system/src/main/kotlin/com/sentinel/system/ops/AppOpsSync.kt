package com.sentinel.system.ops

import android.util.Log
import com.sentinel.data.contract.DataContract
import com.sentinel.data.db.ProtectLevel
import com.sentinel.data.repo.AppConfigRepository
import com.sentinel.data.repo.OpLogRepository
import com.sentinel.system.profile.*
import com.sentinel.system.shell.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppOpsSync(
    @Suppress("unused") private val shell: Shell, private val executor: OpExecutor,
    private val profile: ColorOsProfile?, private val configs: AppConfigRepository,
    private val log: OpLogRepository, private val shizukuState: StateFlow<ShizukuState>,
) {
    private val pending = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = pending.asStateFlow()
    private val mutex = Mutex()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            combine(configs.observeAll(), shizukuState, configs.observeExcluded()) { _, _, _ -> Unit }
                .retryWhen { e, _ ->
                    currentCoroutineContext().ensureActive()
                    Log.w("SentinelSystem", "权限订阅失败", e)
                    delay(DataContract.REFRESH_MS)
                    true
                }.collect {
                    try { syncOnce() }
                    catch (e: Exception) { currentCoroutineContext().ensureActive(); Log.w("SentinelSystem", "权限同步失败", e) }
                }
        }
    }

    suspend fun syncOnce(): Int = mutex.withLock {
        val requested = linkedSetOf<String>()
        val desired = linkedMapOf<String, ProfileOp>()
        val verified = profile?.verifiedAppOps.orEmpty()
        fun request(pkg: String, name: String, mode: String) {
            val id = "appop:$name:$pkg"
            requested += id
            if (name in verified) desired[id] = appOp(pkg, name, mode)
        }
        for (cfg in configs.observeAll().first()) {
            if (!cfg.pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) continue
            val effective = configs.effective(cfg.pkg)
            if (effective.level == ProtectLevel.OFF) continue
            if (effective.limitOverlay) {
                request(cfg.pkg, "SYSTEM_ALERT_WINDOW", "deny")
                profile?.backgroundPopupOp?.takeIf { it.matches(Regex("[A-Za-z0-9_.]+")) }?.let {
                    request(cfg.pkg, it, "ignore")
                }
            }
            if (effective.denyClipboard) {
                request(cfg.pkg, "READ_CLIPBOARD", "ignore")
            }
        }
        val active = log.observeAll().first().filter { it.success && !it.undone && it.opId.startsWith("appop:") }
        val restore = active.filter { it.opId !in requested }
        val unverified = requested.count { it !in desired }
        if (shizukuState.value != ShizukuState.READY) {
            pending.value = requested.size + restore.map { it.opId }.distinct().size
            return@withLock 0
        }
        var changed = 0
        var remaining = unverified
        for (entry in restore.sortedByDescending { it.id }) {
            if (entry.opId.split(':').getOrNull(1) !in verified ||
                shizukuState.value != ShizukuState.READY || !executor.undo(entry.id)) remaining++ else changed++
        }
        for ((id, op) in desired) {
            if (shizukuState.value != ShizukuState.READY) { remaining++; continue }
            if (active.any { it.opId == id }) {
                when (executor.status(op)) {
                    OpStatus.APPLIED -> continue
                    OpStatus.UNKNOWN -> { remaining++; continue }
                    OpStatus.NOT_APPLIED -> Unit
                }
            }
            when (executor.apply(op)) {
                OpOutcome.Applied -> changed++
                OpOutcome.AlreadyApplied -> Unit
                else -> remaining++
            }
        }
        pending.value = remaining
        changed
    }

    private fun appOp(pkg: String, op: String, mode: String) = ProfileOp(
        id = "appop:$op:$pkg", trick = if (op == "READ_CLIPBOARD") 23 else 20,
        title = "$pkg $op", kind = OpKind.APPOP, verified = op in profile?.verifiedAppOps.orEmpty(),
        apply = "appops set $pkg $op $mode", revert = "appops set $pkg $op {before}",
        probe = "appops get $pkg $op", appliedRegex = "(?m)^${Regex.escape(op)}:\\s*$mode(?:[;\\s]|$)",
    )
}
