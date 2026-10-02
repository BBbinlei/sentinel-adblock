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
import kotlinx.serialization.Serializable
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

private enum class Modification { NONE, POSSIBLE, CONFIRMED }
@Serializable private data class Recovery(
    val op: ProfileOp, val before: String, val ts: Long, val logId: Long? = null,
)

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
    private val recoveryJournal = undoFile?.let { AtomicFile(File(it.path + ".recovery")) }
    private val recoveries = recoveryJournal?.let {
        if (it.baseFile.exists() || File(it.baseFile.path + ".bak").exists())
            Json.decodeFromString<Map<String, Recovery>>(it.readFully().decodeToString()).toMutableMap()
        else mutableMapOf()
    } ?: mutableMapOf()

    fun definition(opId: String): ProfileOp? = definitions[opId]
    private fun remember(key: String, op: ProfileOp) {
        definitions[key] = op
        persist(journal, Json.encodeToString(definitions.toMap()))
    }

    private fun persist(file: AtomicFile?, contents: String) {
        file?.let {
            val stream = it.startWrite()
            try {
                stream.write(contents.encodeToByteArray())
                it.finishWrite(stream)
            } catch (e: Exception) { it.failWrite(stream); throw e }
        }
    }

    private fun saveRecoveries() = persist(recoveryJournal, Json.encodeToString(recoveries.toMap()))

    private fun forgetRecovery(opId: String) {
        val recovery = recoveries.remove(opId) ?: return
        try { saveRecoveries() }
        catch (e: Exception) { recoveries[opId] = recovery; throw e }
    }

    private fun recoveryFor(entry: OpLogEntity): Recovery? = recoveries[entry.opId]?.takeIf {
        it.logId == entry.id || (it.logId == null && it.ts == entry.ts && it.before == entry.beforeState &&
            substitute(requireNotNull(it.op.apply), it.before) == entry.command)
    }

    private suspend fun restoreRecovery(recovery: Recovery): Boolean {
        val entries = log.observeAll().first()
        val entry = if (recovery.logId != null) entries.firstOrNull { it.id == recovery.logId }
            else entries.filter { recoveryFor(it) == recovery }.maxByOrNull { it.id }
        if (entry?.undone == true) {
            forgetRecovery(recovery.op.id)
            return false
        }
        if (!shell.exec(revertCommand(recovery.op, recovery.before)).ok) return false
        entry?.id?.let { log.markUndone(it) }
        forgetRecovery(recovery.op.id)
        return true
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
        if (op.id in recoveries) return@withLock OpOutcome.Failed("该操作尚有待恢复记录，请先撤销")
        try {
            val probe = requireNotNull(op.probe)
            val apply = requireNotNull(op.apply)
            requireNotNull(op.revert)
            val regex = Regex(requireNotNull(op.appliedRegex))
            val initial = shell.exec(probe)
            if (!initial.ok) return@withLock OpOutcome.Failed(initial.stderr.ifBlank { "无法读取操作前状态" })
            val before = initial.stdout.trim()
            if (regex.containsMatchIn(before)) return@withLock OpOutcome.AlreadyApplied
            // 修改前验证恢复命令，并持久保存 before；复核失败不取消恢复资格。
            revertCommand(op, before)
            remember(op.id, op)
            val command = substitute(apply, before)
            val recovery = Recovery(op, before, clock.now())
            recoveries[op.id] = recovery
            saveRecoveries()
            val execution = shell.exec(command)
            val after = shell.exec(probe)
            val modification = when {
                !after.ok -> Modification.POSSIBLE
                regex.containsMatchIn(after.stdout.trim()) -> Modification.CONFIRMED
                after.stdout.trim() == before -> Modification.NONE
                else -> Modification.POSSIBLE
            }
            val success = execution.ok && after.ok && regex.containsMatchIn(after.stdout.trim())
            val entry = OpLogEntity(ts = recovery.ts, opId = op.id, target = op.id,
                beforeState = before, command = command, success = success,
                output = listOf(execution.stdout, execution.stderr, after.stdout, after.stderr).filter { it.isNotEmpty() }.joinToString("\n"))
            val id = log.append(entry)
            val recorded = recovery.copy(logId = id)
            recoveries[op.id] = recorded
            saveRecoveries()
            remember("log:$id", op)
            when {
                success || modification == Modification.NONE -> forgetRecovery(op.id)
                else -> withContext(NonCancellable) { runCatching { restoreRecovery(recorded) } }
            }
            if (!success) return@withLock OpOutcome.Failed(after.stderr.ifBlank { "执行或复核失败" })
            try { events.log(null, EventKind.SYSTEM_OP, null, op.title) }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); Log.w("SentinelSystem", "事件写入失败", e) }
            OpOutcome.Applied
        } catch (e: Exception) {
            // 命令/复核抛异常、取消或日志保存失败，也保留失败回滚的记录供重试。
            withContext(NonCancellable) {
                recoveries[op.id]?.let { runCatching { restoreRecovery(it) } }
            }
            currentCoroutineContext().ensureActive()
            OpOutcome.Failed(e.message ?: "操作失败")
        }
    }

    suspend fun applyAll(ops: List<ProfileOp>): Map<String, OpOutcome> = buildMap {
        for (op in ops) put(op.id, apply(op))
    }

    suspend fun undo(logId: Long): Boolean = mutex.withLock {
        try {
            val entry = log.observeAll().first().firstOrNull { it.id == logId && !it.undone }
                ?: return@withLock false
            undoEntry(entry)
        } catch (e: Exception) { currentCoroutineContext().ensureActive(); false }
    }

    private suspend fun undoEntry(entry: OpLogEntity): Boolean {
        recoveryFor(entry)?.let { return restoreRecovery(it.copy(logId = entry.id)) }
        // 先恢复最近一次未完成的修改，避免旧日志覆盖它的恢复基线。
        if (entry.opId in recoveries) return false
        if (!entry.success) return false
        val op = definitions["log:${entry.id}"] ?: definitions[entry.opId] ?: return false
        if (!shell.exec(revertCommand(op, entry.beforeState)).ok) return false
        log.markUndone(entry.id)
        return true
    }

    suspend fun undoAll(): Int = mutex.withLock {
        val entries = log.observeAll().first().filter { !it.undone && (it.success || recoveryFor(it) != null) }
            .sortedWith(compareByDescending<OpLogEntity> { it.ts }.thenByDescending { it.id })
        val unlogged = recoveries.values.filter { recovery -> entries.none { recoveryFor(it) == recovery } }
        var restored = 0
        // 日志落库前进程中断的恢复信息也不能丢失。
        for (recovery in unlogged.sortedByDescending { it.ts }) {
            try { if (restoreRecovery(recovery)) restored++ }
            catch (e: Exception) { currentCoroutineContext().ensureActive() }
        }
        for (entry in entries) {
            try { if (undoEntry(entry)) restored++ }
            catch (e: Exception) { currentCoroutineContext().ensureActive() }
        }
        restored
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
