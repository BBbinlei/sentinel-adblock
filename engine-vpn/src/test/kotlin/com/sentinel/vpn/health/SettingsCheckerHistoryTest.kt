package com.sentinel.vpn.health

import android.content.Context
import android.content.SharedPreferences
import com.sentinel.data.Clock
import com.sentinel.data.db.*
import com.sentinel.vpn.fakes.MemoryData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class SettingsCheckerHistoryTest {
    // Test the private production adapter, not an independently implemented checker.
    private val type = Class.forName("com.sentinel.vpn.service.SentinelVpnService\$SettingsChecker")
    private fun checker(context: Context, history: SharedPreferences): EnabledServicesChecker =
        type.getDeclaredConstructor(Context::class.java, SharedPreferences::class.java)
            .apply { isAccessible = true }.newInstance(context, history) as EnabledServicesChecker
    private fun remember(checker: EnabledServicesChecker, states: Map<EngineId, EngineStatusEntity>) {
        type.getDeclaredMethod("remember", Map::class.java).apply { isAccessible = true }.invoke(checker, states)
    }

    @Test fun `R15-01 RUNNING then STOPPED or DEGRADED remains wanted after checker recreation`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        for (finalState in listOf(EngineState.STOPPED, EngineState.DEGRADED)) {
            val history = context.getSharedPreferences("r15-${finalState.name}", Context.MODE_PRIVATE)
            assertTrue(history.edit().clear().commit())
            MemoryData(Clock { 100L }, coroutineContext).use { data ->
                val first = checker(context, history)
                for (engine in listOf(EngineId.A11Y, EngineId.NOTIFY)) {
                    data.status.report(engine, EngineState.RUNNING)
                    remember(first, data.status.observeAll().first())
                    data.status.report(engine, finalState, "test stopped or degraded")
                    remember(first, data.status.observeAll().first())
                }
                val restored = checker(context, context.getSharedPreferences("r15-${finalState.name}", Context.MODE_PRIVATE))
                assertTrue(restored.userWantsA11y()); assertTrue(restored.userWantsNotify())
                assertTrue(history.getBoolean(EngineId.A11Y.name, false)); assertTrue(history.getBoolean(EngineId.NOTIFY.name, false))
                assertFalse(restored.accessibilityEnabled()); assertFalse(restored.notificationListenerEnabled())
                val notifications = mutableListOf<EngineId>()
                ServiceWatchdog(restored, data.status, notifications::add).checkOnce()
                assertEquals(listOf(EngineId.A11Y, EngineId.NOTIFY), notifications)
                assertTrue(data.status.observeAll().first().values.all { it.state == EngineState.STOPPED })
            }
        }
    }

    @Test fun `R15-02 empty and NOT_SETUP histories never notify and engines remain independent`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val history = context.getSharedPreferences("r15-never", Context.MODE_PRIVATE)
        assertTrue(history.edit().clear().commit())
        MemoryData(Clock { 100L }, coroutineContext).use { data ->
            val checker = checker(context, history); val notices = mutableListOf<EngineId>()
            val watchdog = ServiceWatchdog(checker, data.status, notices::add)
            remember(checker, emptyMap()); watchdog.checkOnce()
            for (engine in listOf(EngineId.A11Y, EngineId.NOTIFY)) data.status.report(engine, EngineState.NOT_SETUP)
            remember(checker, data.status.observeAll().first()); watchdog.checkOnce()
            assertFalse(checker.userWantsA11y()); assertFalse(checker.userWantsNotify()); assertTrue(notices.isEmpty())
            data.status.report(EngineId.A11Y, EngineState.RUNNING)
            remember(checker, data.status.observeAll().first())
            data.status.report(EngineId.A11Y, EngineState.NOT_SETUP)
            remember(checker, data.status.observeAll().first()); remember(checker, emptyMap())
            assertTrue(checker.userWantsA11y()); assertFalse(checker.userWantsNotify())
            watchdog.checkOnce(); assertEquals(listOf(EngineId.A11Y), notices)
        }
    }

    @Test fun `R15-03 existing STOPPED and DEGRADED rows migrate into persistent history`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val history = context.getSharedPreferences("r15-migration", Context.MODE_PRIVATE)
        assertTrue(history.edit().clear().commit())
        MemoryData(Clock { 100L }, coroutineContext).use { data ->
            data.status.report(EngineId.A11Y, EngineState.STOPPED)
            data.status.report(EngineId.NOTIFY, EngineState.DEGRADED, "test migration")
            val checker = checker(context, history)
            remember(checker, data.status.observeAll().first())
            assertTrue(checker.userWantsA11y()); assertTrue(checker.userWantsNotify())
        }
    }
}
