package com.sentinel.system.ops

import com.sentinel.data.db.OpLogDao
import com.sentinel.data.db.OpLogEntity
import com.sentinel.data.repo.OpLogRepository
import com.sentinel.system.fakes.*
import com.sentinel.system.shell.ExecResult
import com.sentinel.system.shell.Shell
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.*

class OpExecutorRecoveryTest : MemoryDataTest() {
    @get:Rule val temp = TemporaryFolder()
    private val op = settingOp()
    private val restore = "settings put secure recommend 7"

    private fun device() = FakeDevice().apply { settings["secure" to "recommend"] = "7" }

    private fun failedVerification(device: FakeDevice, throws: Boolean = false): Shell = object : Shell {
        private var probes = 0
        override suspend fun exec(cmd: String): ExecResult {
            if (cmd == op.probe && ++probes == 2) {
                device.commands += cmd
                if (throws) error("verification disconnected")
                return ExecResult(-2, "", "verification disconnected")
            }
            return device.exec(cmd)
        }
    }

    @Test fun R04_01_successful_change_failed_verification_rolls_back() = runTest {
        val device = device()
        val before = device.snapshot()
        val executor = OpExecutor(failedVerification(device), logs, events, clock)
        assertIs<OpOutcome.Failed>(executor.apply(op))
        assertEquals(listOf(op.probe, op.apply, op.probe, restore), device.commands)
        assertEquals(before, device.snapshot())
        val entry = logs.observeAll().first().single()
        assertEquals("7", entry.beforeState)
        assertFalse(entry.success)
        assertTrue(entry.undone)
        assertFalse(executor.undo(entry.id))
        assertEquals(0, executor.undoAll())
        assertEquals(4, device.commands.size)
    }

    @Test fun R04_02_verification_exception_still_rolls_back() = runTest {
        val device = device()
        val before = device.snapshot()
        assertIs<OpOutcome.Failed>(OpExecutor(failedVerification(device, throws = true), logs, events, clock).apply(op))
        assertEquals(restore, device.commands.last())
        assertEquals(before, device.snapshot())
    }

    @Test fun R04_03_failed_rollback_survives_restart_and_undo_retries() = runTest {
        val device = device().apply { failures += restore }
        val journal = File(temp.root, "undo.json")
        val executor = OpExecutor(failedVerification(device), logs, events, clock, journal)
        assertIs<OpOutcome.Failed>(executor.apply(op))
        assertEquals("0", device.settings["secure" to "recommend"])
        val entry = logs.observeAll().first().single()
        assertFalse(entry.success)
        assertFalse(entry.undone)
        assertEquals(restore, device.commands.last())

        val restarted = OpExecutor(device, logs, events, clock, journal)
        val start = device.commands.size
        assertIs<OpOutcome.Failed>(restarted.apply(op))
        assertEquals(start, device.commands.size, "pending recovery must prevent another modification")
        assertFalse(restarted.undo(entry.id))
        assertFalse(logs.observeAll().first().single().undone)
        device.failures.clear()
        assertTrue(restarted.undo(entry.id))
        assertEquals("7", device.settings["secure" to "recommend"])
        assertTrue(logs.observeAll().first().single().undone)
        assertFalse(restarted.undo(entry.id))
        assertEquals(0, restarted.undoAll())
    }

    @Test fun R04_04_failed_rollback_survives_restart_and_undo_all_retries() = runTest {
        val device = device().apply { failures += restore }
        val journal = File(temp.root, "undo.json")
        assertIs<OpOutcome.Failed>(OpExecutor(failedVerification(device), logs, events, clock, journal).apply(op))
        val restarted = OpExecutor(device, logs, events, clock, journal)
        assertEquals(0, restarted.undoAll())
        assertEquals("0", device.settings["secure" to "recommend"])
        assertFalse(logs.observeAll().first().single().undone)
        device.failures.clear()
        assertEquals(1, restarted.undoAll())
        assertEquals("7", device.settings["secure" to "recommend"])
        assertTrue(logs.observeAll().first().single().undone)
        assertEquals(0, OpExecutor(device, logs, events, clock, journal).undoAll())
    }

    @Test fun R04_05_cancel_before_logging_preserves_durable_unlogged_recovery() = runTest {
        val device = device().apply { failures += restore }
        val journal = File(temp.root, "undo.json")
        val changed = CompletableDeferred<Unit>()
        val shell = object : Shell {
            private var probes = 0
            override suspend fun exec(cmd: String): ExecResult {
                if (cmd == op.apply) {
                    // 修改前已有恢复基线，不能依赖之后的日志写入。
                    assertTrue(File(journal.path + ".recovery").readText().contains("\"before\":\"7\""))
                }
                if (cmd == op.probe && ++probes == 2) {
                    changed.complete(Unit)
                    CompletableDeferred<Unit>().await()
                }
                return device.exec(cmd)
            }
        }
        val executor = OpExecutor(shell, logs, events, clock, journal)
        val job = launch { executor.apply(op) }
        changed.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(restore, device.commands.last(), "cancellation must still attempt rollback")
        assertEquals("0", device.settings["secure" to "recommend"])
        assertTrue(logs.observeAll().first().isEmpty())

        val restarted = OpExecutor(device, logs, events, clock, journal)
        val count = device.commands.size
        assertIs<OpOutcome.Failed>(restarted.apply(op))
        assertEquals(count, device.commands.size)
        device.failures.clear()
        assertEquals(1, restarted.undoAll())
        assertEquals("7", device.settings["secure" to "recommend"])
        assertEquals(0, OpExecutor(device, logs, events, clock, journal).undoAll())
    }

    @Test fun R04_06_log_write_failure_preserves_recovery_for_restart() = runTest {
        val device = device().apply { failures += restore }
        val journal = File(temp.root, "undo.json")
        var attempted: OpLogEntity? = null
        val failingLog = OpLogRepository(object : OpLogDao by db.opLogDao() {
            override suspend fun insert(entity: OpLogEntity): Long {
                attempted = entity
                error("injected log write failure")
            }
        }, clock)
        assertIs<OpOutcome.Failed>(OpExecutor(device, failingLog, events, clock, journal).apply(op))
        assertTrue(assertNotNull(attempted).success, "the modification and verification succeeded before log failure")
        assertEquals(restore, device.commands.last())
        assertEquals("0", device.settings["secure" to "recommend"])
        assertTrue(logs.observeAll().first().isEmpty())
        device.failures.clear()
        assertEquals(1, OpExecutor(device, logs, events, clock, journal).undoAll())
        assertEquals("7", device.settings["secure" to "recommend"])
        assertEquals(0, OpExecutor(device, logs, events, clock, journal).undoAll())
    }
}
