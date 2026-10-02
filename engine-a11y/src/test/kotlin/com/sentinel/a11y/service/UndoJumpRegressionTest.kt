package com.sentinel.a11y.service

import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import com.sentinel.a11y.core.A11yAction
import com.sentinel.a11y.fakes.*
import com.sentinel.data.db.JumpExceptionDao
import com.sentinel.data.db.JumpExceptionEntity
import com.sentinel.data.db.SignalKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import kotlin.test.*

class UndoJumpRegressionTest : ServiceTestData() {
    private fun assertTargetStarted() {
        val intent = assertNotNull(shadowOf(context).nextStartedActivity)
        assertEquals(installActivity(TAOBAO), intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    private suspend fun assertUndoSignal() {
        val signal = runtime.signals.observeUnhandled().first().single()
        assertEquals(APP, signal.pkg)
        assertEquals(SignalKind.USER_UNDO, signal.kind)
        assertEquals(time.now, signal.ts)
        assertNull(signal.ruleId)
    }

    @Test fun R12_01_exception_write_failure_still_restores_and_emits_USER_UNDO() = runTest {
        var restoreAttempted = false
        runtime = makeRuntime(object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                assertTrue(runtime.state.excepted(APP, TAOBAO), "temporary exception must precede restoration")
                restoreAttempted = true
                super.startActivity(intent)
            }
        })
        sql("CREATE TRIGGER fail_exception BEFORE INSERT ON jump_exceptions BEGIN SELECT RAISE(ABORT, 'injected exception write failure'); END")
        UndoJump.run(runtime, APP, TAOBAO)
        assertTrue(restoreAttempted)
        assertTrue(ShadowLog.getLogs().any { it.throwable?.message?.contains("injected exception write failure") == true })
        assertFalse(dao<JumpExceptionDao>("jumpExceptionDao").isExcepted(APP, TAOBAO))
        assertUndoSignal()
        assertTargetStarted()
        assertTrue(runtime.state.excepted(APP, TAOBAO))
    }

    @Test fun R12_04_target_restore_failure_does_not_lose_exception_or_signal() = runTest {
        var attempted: Intent? = null
        runtime = makeRuntime(object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                attempted = intent
                throw SecurityException("injected background launch denied")
            }
        })
        UndoJump.run(runtime, APP, TAOBAO)
        assertEquals(installActivity(TAOBAO), assertNotNull(attempted).component)
        assertEquals(listOf(JumpExceptionEntity(APP, TAOBAO)), dao<JumpExceptionDao>("jumpExceptionDao").all())
        assertUndoSignal()
        assertNull(shadowOf(context).nextStartedActivity)
        assertTrue(ShadowLog.getLogs().any { it.throwable?.message == "injected background launch denied" })
    }

    @Test fun R12_02_signal_write_failure_still_saves_exception_and_restores() = runTest {
        sql("CREATE TRIGGER fail_signal BEFORE INSERT ON signals BEGIN SELECT RAISE(ABORT, 'injected signal write failure'); END")
        UndoJump.run(runtime, APP, TAOBAO)
        assertTrue(ShadowLog.getLogs().any { it.throwable?.message?.contains("injected signal write failure") == true })
        assertTrue(runtime.signals.observeUnhandled().first().isEmpty())
        assertEquals(listOf(JumpExceptionEntity(APP, TAOBAO)), dao<JumpExceptionDao>("jumpExceptionDao").all())
        assertTargetStarted()
    }

    @Test fun R12_03_failed_persistence_temporary_exception_survives_cache_refresh_and_prevents_revert() = runTest {
        runtime.prefetch(listOf(APP, TAOBAO), APP to TAOBAO)
        val brain = brain()
        brain.onInput(window(HOME, time.now), null)
        brain.onInput(window(APP, time.now + 100), null)
        assertEquals(1, brain.onInput(window(TAOBAO, time.now + 1_000), null).filterIsInstance<A11yAction.Revert>().size)
        sql("CREATE TRIGGER fail_exception BEFORE INSERT ON jump_exceptions BEGIN SELECT RAISE(ABORT, 'injected exception write failure'); END")
        UndoJump.run(runtime, APP, TAOBAO)
        assertFalse(dao<JumpExceptionDao>("jumpExceptionDao").isExcepted(APP, TAOBAO))
        time.now += 10_000 // 超过快照 TTL，重新读库得到 false。
        runtime.prefetch(listOf(APP, TAOBAO), APP to TAOBAO)
        assertTrue(runtime.state.excepted(APP, TAOBAO))
        assertFalse(runtime.state.excepted(APP, PINDUODUO))
        assertFalse(runtime.state.excepted(TAOBAO, APP))
        brain.onInput(window(HOME, time.now), null)
        brain.onInput(window(APP, time.now + 100), null)
        assertTrue(brain.onInput(window(TAOBAO, time.now + 1_000), null).none { it is A11yAction.Revert })
        val restarted = makeRuntime()
        restarted.prefetch(listOf(APP, TAOBAO), APP to TAOBAO)
        assertFalse(restarted.state.excepted(APP, TAOBAO), "temporary exceptions belong only to this runtime")
    }

    @Test fun R12_MT_AY_03_executor_undo_button_writes_exception_and_USER_UNDO_and_prevents_repeat() = runTest {
        runtime.prefetch(listOf(APP, TAOBAO), APP to TAOBAO)
        val brain = brain()
        brain.onInput(window(HOME, time.now), null)
        brain.onInput(window(APP, time.now + 100), null)
        val revert = brain.onInput(window(TAOBAO, time.now + 1_000), null).filterIsInstance<A11yAction.Revert>().single()
        val windows = WindowProbe()
        val service = Robolectric.buildService(OverlayHostService::class.java).create().get().apply { this.windows = windows }
        val overlay = OverlayToast(service)
        try {
            ActionExecutor(service, runtime, overlay, this, {}, {}).execute(listOf(revert))
            assertEquals(installActivity(APP), assertNotNull(shadowOf(context).nextStartedActivity).component)
            shadowOf(Looper.getMainLooper()).idle()
            val undo = (windows.added.single() as LinearLayout).getChildAt(1) as Button
            assertEquals("撤销", undo.text.toString())
            assertTrue(undo.performClick())
            coroutineContext[Job]!!.children.toList().joinAll()
            assertEquals(listOf(JumpExceptionEntity(APP, TAOBAO)), dao<JumpExceptionDao>("jumpExceptionDao").all())
            assertUndoSignal()
            assertTargetStarted()
            brain.onInput(window(HOME, time.now + 10_000), null)
            brain.onInput(window(APP, time.now + 10_100), null)
            assertTrue(brain.onInput(window(TAOBAO, time.now + 11_000), null).none { it is A11yAction.Revert })
        } finally { overlay.close() }
    }
}
