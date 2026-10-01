package com.sentinel.system.ops

import com.sentinel.rules.model.EventKind
import com.sentinel.system.fakes.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.*

class OpExecutorTest : MemoryDataTest() {
    private fun ScriptedShell.success(op: com.sentinel.system.profile.ProfileOp, before: String = "1") {
        expect(requireNotNull(op.probe), "$before\n")
        expect(requireNotNull(op.apply))
        expect(requireNotNull(op.probe), "0\n")
    }

    // UT-SY-3-01
    @Test fun UT_SY_3_01_unverified_never_executes() = runTest {
        val shell = ScriptedShell()
        assertEquals(OpOutcome.NotVerified, OpExecutor(shell, logs, events).apply(settingOp(verified = false)))
        assertEquals(emptyList(), shell.commands)
        assertEquals(emptyList(), logs.observeAll().first())
    }
    // UT-SY-3-02
    @Test fun UT_SY_3_02_already_applied_only_probes() = runTest {
        val op = settingOp()
        val shell = ScriptedShell().apply { expect(requireNotNull(op.probe), "0\n") }
        assertEquals(OpOutcome.AlreadyApplied, OpExecutor(shell, logs, events).apply(op))
        assertEquals(listOf(op.probe), shell.commands)
        assertEquals(emptyList(), logs.observeAll().first())
        shell.assertConsumed()
    }
    // UT-SY-3-03
    @Test fun UT_SY_3_03_success_records_before_and_system_event() = runTest {
        val op = settingOp()
        val shell = ScriptedShell().apply { success(op, "7") }
        assertEquals(OpOutcome.Applied, OpExecutor(shell, logs, events).apply(op))
        val entry = logs.observeAll().first().single()
        assertEquals(op.id, entry.opId)
        assertEquals("7", entry.beforeState.trim())
        assertEquals(op.apply, entry.command)
        assertTrue(entry.success)
        assertFalse(entry.undone)
        assertEquals(EventKind.SYSTEM_OP, events.observeRecent(setOf(EventKind.SYSTEM_OP), 10).first().single().kind)
        shell.assertConsumed()
    }
    // UT-SY-3-04
    @Test fun UT_SY_3_04_post_probe_mismatch_records_failure() = runTest {
        val op = settingOp()
        val shell = ScriptedShell().apply {
            expect(requireNotNull(op.probe), "1\n")
            expect(requireNotNull(op.apply))
            expect(requireNotNull(op.probe), "1\n")
        }
        val result = OpExecutor(shell, logs, events).apply(op)
        assertIs<OpOutcome.Failed>(result)
        val entry = logs.observeAll().first().single()
        assertEquals(op.id, entry.opId)
        assertEquals("1", entry.beforeState.trim())
        assertFalse(entry.success)
        assertEquals(emptyList(), events.observeRecent(setOf(EventKind.SYSTEM_OP), 10).first())
        shell.assertConsumed()
    }
    // UT-SY-3-05
    @Test fun UT_SY_3_05_undo_substitutes_original_before_value() = runTest {
        val op = settingOp()
        val shell = ScriptedShell().apply {
            success(op, "7")
            expect("settings put secure recommend 7")
        }
        val executor = OpExecutor(shell, logs, events)
        assertEquals(OpOutcome.Applied, executor.apply(op))
        val entry = logs.observeAll().first().single()
        assertTrue(executor.undo(entry.id))
        assertTrue(logs.observeAll().first().single().undone)
        assertEquals("settings put secure recommend 7", shell.commands.last())
        assertFalse(shell.commands.any { "{before}" in it })
        shell.assertConsumed()
    }
    // UT-SY-3-06
    @Test fun UT_SY_3_06_apply_all_continues_after_failure() = runTest {
        val bad = settingOp("bad")
        val good = settingOp("good")
        val shell = ScriptedShell().apply {
            expect(requireNotNull(bad.probe), "1\n")
            expect(requireNotNull(bad.apply))
            expect(requireNotNull(bad.probe), "1\n")
            success(good)
        }
        val result = OpExecutor(shell, logs, events).applyAll(listOf(bad, good))
        assertEquals(setOf("bad", "good"), result.keys)
        assertIs<OpOutcome.Failed>(result["bad"])
        assertEquals(OpOutcome.Applied, result["good"])
        assertEquals(mapOf("bad" to false, "good" to true), logs.observeAll().first().associate { it.opId to it.success })
        shell.assertConsumed()
    }
    // UT-SY-3-07
    @Test fun UT_SY_3_07_undo_all_only_successful_not_yet_undone() = runTest {
        val device = FakeDevice()
        val ops = listOf(settingOp("one"), settingOp("already-undone"), settingOp("two"), settingOp("failed"))
        ops.forEach { device.settings["secure" to it.id] = "1" }
        device.failures += requireNotNull(ops.last().apply)
        val executor = OpExecutor(device, logs, events)
        val result = executor.applyAll(ops)
        assertIs<OpOutcome.Failed>(result["failed"])
        assertEquals(3, result.values.count { it == OpOutcome.Applied })
        assertTrue(executor.undo(requireNotNull(logs.latestApplied("already-undone")).id))
        val start = device.commands.size
        assertEquals(2, executor.undoAll())
        val restored = device.commands.drop(start).filter { it.startsWith("settings put") }
        assertEquals(setOf("settings put secure one 1", "settings put secure two 1"), restored.toSet())
        assertEquals(2, restored.size)
        assertTrue(logs.observeAll().first().filter { it.success }.all { it.undone })
        assertFalse(logs.observeAll().first().single { !it.success }.undone)
        val end = device.commands.size
        assertEquals(0, executor.undoAll())
        assertEquals(end, device.commands.size)
    }
}
