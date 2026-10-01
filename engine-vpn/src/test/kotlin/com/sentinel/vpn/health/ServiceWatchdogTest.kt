package com.sentinel.vpn.health

import com.sentinel.data.Clock
import com.sentinel.data.db.*
import com.sentinel.vpn.fakes.MemoryData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class ServiceWatchdogTest {
    private class Checker(var a11y: Boolean = true, var notify: Boolean = true, var wantsA11y: Boolean = true, var wantsNotify: Boolean = true) : EnabledServicesChecker {
        override fun accessibilityEnabled() = a11y
        override fun notificationListenerEnabled() = notify
        override fun userWantsA11y() = wantsA11y
        override fun userWantsNotify() = wantsNotify
    }
    @Test fun `UT-VP-6-02 disabled wanted accessibility reports STOPPED and notifies`() = runTest {
        MemoryData(Clock { 0L }, coroutineContext).use { data ->
            data.status.report(EngineId.A11Y, EngineState.RUNNING)
            val notifications = mutableListOf<EngineId>(); val checker = Checker(a11y = false)
            ServiceWatchdog(checker, data.status, notifications::add).checkOnce()
            assertEquals(EngineState.STOPPED, data.status.observeAll().first()[EngineId.A11Y]?.state)
            assertEquals(listOf(EngineId.A11Y), notifications)
        }
    }
    @Test fun `UT-VP-6-03 healthy or never-enabled services do not notify`() = runTest {
        MemoryData(Clock { 0L }, coroutineContext).use { data ->
            val notifications = mutableListOf<EngineId>(); val checker = Checker()
            data.status.report(EngineId.A11Y, EngineState.RUNNING); data.status.report(EngineId.NOTIFY, EngineState.RUNNING)
            val watchdog = ServiceWatchdog(checker, data.status, notifications::add); watchdog.checkOnce()
            assertTrue(notifications.isEmpty()); assertEquals(EngineState.RUNNING, data.status.observeAll().first()[EngineId.A11Y]?.state)
            assertEquals(EngineState.RUNNING, data.status.observeAll().first()[EngineId.NOTIFY]?.state)
            checker.a11y = false; checker.notify = false; checker.wantsA11y = false; checker.wantsNotify = false
            watchdog.checkOnce(); assertTrue(notifications.isEmpty())
        }
    }
}
