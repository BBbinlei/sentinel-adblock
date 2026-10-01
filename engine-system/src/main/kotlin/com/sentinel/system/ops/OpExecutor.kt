package com.sentinel.system.ops

import android.util.AtomicFile
import android.util.Log
import com.sentinel.data.Clock
import com.sentinel.data.db.OpLogEntity
import com.sentinel.data.repo.EventRepository
import com.sentinel.data.repo.OpLogRepository
import com.sentinel.rules.model.EventKind
import com.sentinel.system.profile.*
import com.sentinel.system.shell.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

sealed interface OpOutcome {
    data object Applied : OpOutcome
    data object AlreadyApplied : OpOutcome
    data object NotVerified : OpOutcome
    data object WizardOnly : OpOutcome
    data class Failed(val reason: String) : OpOutcome
}
enum class OpStatus { APPLIED, NOT_APPLIED, UNKNOWN }

class OpExecutor(
    private val shell: Shell, private val log: OpLogRepository, private val events: EventRepository,
    private val clock: Clock = Clock(System::currentTimeMillis), undoFile: File? = null,
) {
    // ponytail: 串行修改系统状态；只有测得吞吐不足时再按操作分锁。
    private val mutex = Mutex()
    private val journal = undoFile?.let(::AtomicFile)
    private val definitions = journal?.let {
        if (it.baseFile.exists()) Json.decodeFromString<Map<String, ProfileOp>>(it.readFully().decodeToString()).toMutableMap()
        else mutableMapOf()
    } ?: mutableMapOf()

    fun definition(opId: String): ProfileOp? = definitions[opId]
    private fun remember(key: String, op: ProfileOp) {
        definitions[key] = op
        journal?.let {
            val stream = it.startWrite()
            try {
                stream.write(Json.encodeToString(definitions.toMap()).encodeToByteArray())
                it.finishWrite(stream)
            } catch (e: Exception) { it.failWrite(stream); throw e }
        }
    }

    suspend fun status(op: ProfileOp): OpStatus {
        if (!op.verified || op.kind == OpKind.WIZARD || op.probe == null || op.appliedRegex == null) return OpStatus.UNKNOWN
        return try {
            val result = shell.exec(op.probe)
            if (!result.ok) OpStatus.UNKNOWN
            else if (Regex(op.appliedRegex).containsMatchIn(result.stdout.trim())) OpStatus.APPLIED else OpStatus.NOT_APPLIED
        } catch (e: Exception) { currentCoroutineContext().ensureActive(); OpStatus.UNKNOWN }
    }

    suspend fun apply(op: ProfileOp): OpOutcome = mutex.withLock {
        if (!op.verified) return@withLock OpOutcome.NotVerified
        if (op.kind == OpKind.WIZARD) return@withLock OpOutcome.WizardOnly
        try {
            val probe = requireNotNull(op.probe)
            val apply = requireNotNull(op.apply)
            requireNotNull(op.revert)
            val regex = Regex(requireNotNull(op.appliedRegex))
            val initial = shell.exec(probe)
            if (!initial.ok) return@withLock OpOutcome.Failed(initial.stderr.ifBlank { "无法读取操作前状态" })
            val before = initial.stdout.trim()
            if (regex.containsMatchIn(before)) return@withLock OpOutcome.AlreadyApplied
            // 在修改系统前保存定义；重启后撤销不依赖当前档案仍有这条操作。
            remember(op.id, op)
            val command = substitute(apply, before)
            val execution = shell.exec(command)
            val after = if (execution.ok) shell.exec(probe) else execution
            val success = execution.ok && after.ok && regex.containsMatchIn(after.stdout.trim())
            val entry = OpLogEntity(ts = clock.now(), opId = op.id, target = op.id,
                beforeState = before, command = command, success = success,
                output = listOf(execution.stdout, execution.stderr, after.stdout, after.stderr).filter { it.isNotEmpty() }.joinToString("\n"))
            try {
                val id = log.append(entry)
                remember("log:$id", op)
            } catch (e: Exception) {
                // 日志保存失败时尽力回滚，不能静默丢掉恢复所需的 before。
                withContext(NonCancellable) { runCatching { shell.exec(revertCommand(op, before)) } }
                throw e
            }
            if (!success) return@withLock OpOutcome.Failed(after.stderr.ifBlank { "执行或复核失败" })
            try { events.log(null, EventKind.SYSTEM_OP, null, op.title) }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); Log.w("SentinelSystem", "事件写入失败", e) }
            OpOutcome.Applied
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            OpOutcome.Failed(e.message ?: "操作失败")
        }
    }

    suspend fun applyAll(ops: List<ProfileOp>): Map<String, OpOutcome> = buildMap {
        for (op in ops) put(op.id, apply(op))
    }

    suspend fun undo(logId: Long): Boolean = mutex.withLock {
        try {
            val entry = log.observeAll().first().firstOrNull { it.id == logId && it.success && !it.undone }
                ?: return@withLock false
            val op = definitions["log:$logId"] ?: definitions[entry.opId] ?: return@withLock false
            if (shell.exec(revertCommand(op, entry.beforeState)).ok) {
                log.markUndone(logId)
                true
            } else false
        } catch (e: Exception) { currentCoroutineContext().ensureActive(); false }
    }

    suspend fun undoAll(): Int {
        val entries = log.observeAll().first().filter { it.success && !it.undone }
            .sortedWith(compareByDescending<OpLogEntity> { it.ts }.thenByDescending { it.id })
        var restored = 0
        for (entry in entries) if (undo(entry.id)) restored++
        return restored
    }

    private fun revertCommand(op: ProfileOp, before: String): String {
        val revert = requireNotNull(op.revert)
        // settings get 对不存在的键返回 null；还原应删除键，而非写入字符串 null。
        if (op.kind == OpKind.SETTING && before.trim() == "null") {
            val match = Regex("^settings put (system|secure|global) ([A-Za-z0-9_.-]+) \\{before}$").matchEntire(revert)
            if (match != null) return "settings delete ${match.groupValues[1]} ${match.groupValues[2]}"
        }
        val value = if (op.kind == OpKind.APPOP) {
            val mode = Regex("(?:^|\\s|:)\\s*(allow|deny|ignore|default|foreground|errored)(?:[;\\s]|$)")
                .find(before)?.groupValues?.get(1) ?: error("无法解析原 AppOps 模式")
            mode
        } else before.trim()
        return substitute(revert, value)
    }

    private fun substitute(command: String, before: String): String {
        val safe = if (before.matches(Regex("[A-Za-z0-9_.:/+-]+"))) before
            else "'" + before.replace("'", "'\\''") + "'"
        return command.replace("{before}", safe)
    }
}
