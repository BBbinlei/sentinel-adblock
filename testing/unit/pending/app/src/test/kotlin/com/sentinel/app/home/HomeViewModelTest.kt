package com.sentinel.app.home

import com.sentinel.app.fakes.*
import com.sentinel.data.db.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest : AppTest() {
    private fun vm() = keep(HomeViewModel(data.global, data.events, data.statuses,
        data.overrides, data.apps, data.clock, privateDnsWarning = { null }))

    @Test fun UT_AP_2_01_shield_follows_enabled_pause_and_vpn() = runTest(main.dispatcher) {
        data.statusRows.value = listOf(EngineStatusEntity(EngineId.VPN, EngineState.RUNNING, null, data.clock.now()))
        val vm = vm(); watch(vm.state)
        assertEquals(ShieldState.PROTECTING, vm.state.value.shield)
        data.globalRows.value = GlobalStateEntity(enabled = true, pausedUntil = data.clock.now() + 300_000)
        runCurrent(); assertEquals(ShieldState.PAUSED, vm.state.value.shield)
        data.globalRows.value = GlobalStateEntity(enabled = false)
        runCurrent(); assertEquals(ShieldState.OFF, vm.state.value.shield)
    }

    @Test fun UT_AP_2_02_shield_writes_disable_or_enable_and_resume() = runTest(main.dispatcher) {
        data.statusRows.value = listOf(EngineStatusEntity(EngineId.VPN, EngineState.RUNNING, null, data.clock.now()))
        val expiredPause = data.clock.now() - 1
        data.globalRows.value = GlobalStateEntity(pausedUntil = expiredPause)
        val vm = vm(); watch(vm.state)
        vm.onShieldTapped(); runCurrent()
        assertEquals(GlobalStateEntity(enabled = false, pausedUntil = expiredPause), data.globalRows.value)
        vm.onShieldTapped(); runCurrent()
        assertTrue(data.globalRows.value.enabled)
        assertNull(data.globalRows.value.pausedUntil)
        data.globalRows.value = GlobalStateEntity(pausedUntil = data.clock.now() + 300_000)
        runCurrent()
        vm.onShieldTapped(); runCurrent()
        assertTrue(data.globalRows.value.enabled)
        assertNull(data.globalRows.value.pausedUntil)
    }

    @Test fun UT_AP_2_03_degraded_message_is_warning() = runTest(main.dispatcher) {
        val warning = "规则不可用，暂停网络拦截"
        data.statusRows.value = listOf(EngineStatusEntity(EngineId.VPN, EngineState.DEGRADED, warning, data.clock.now()))
        val vm = vm(); watch(vm.state)
        assertContains(vm.state.value.warnings, warning)
        data.statusRows.value = listOf(EngineStatusEntity(EngineId.VPN, EngineState.RUNNING, null, data.clock.now()))
        runCurrent(); assertFalse(warning in vm.state.value.warnings)
    }

}
