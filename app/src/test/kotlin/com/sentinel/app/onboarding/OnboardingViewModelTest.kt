package com.sentinel.app.onboarding

import android.content.Context
import com.sentinel.app.fakes.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest : AppTest() {
    private val checker = FakeSetupChecker()
    private fun vm(): OnboardingViewModel {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("sentinel", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return keep(OnboardingViewModel(checker, prefs))
    }

    @Test fun UT_AP_3_01_resume_checks_all_and_selects_first_missing() = runTest(main.dispatcher) {
        checker.completed += setOf(SetupStep.SHIZUKU, SetupStep.ACCESSIBILITY)
        val vm = vm(); watch(vm.state); vm.onResume(); runCurrent()
        assertEquals(checker.completed, vm.state.value.done)
        assertEquals(SetupStep.VPN, vm.state.value.current)
        assertFalse(vm.state.value.finished)
        checker.completed += SetupStep.VPN
        vm.onResume(); runCurrent()
        assertEquals(checker.completed, vm.state.value.done)
        assertEquals(SetupStep.NOTIFICATION, vm.state.value.current)
    }

    @Test fun UT_AP_3_02_skip_advances_without_completing() = runTest(main.dispatcher) {
        val vm = vm(); watch(vm.state); vm.onResume(); vm.skip(); runCurrent()
        assertEquals(SetupStep.VPN, vm.state.value.current)
        assertTrue(vm.state.value.done.isEmpty())
        assertFalse(vm.state.value.finished)
    }

    @Test fun UT_AP_3_03_all_done_finishes_and_persists() = runTest(main.dispatcher) {
        checker.completed += SetupStep.entries
        val vm = vm(); watch(vm.state); vm.onResume(); runCurrent()
        assertEquals(SetupStep.entries.toSet(), vm.state.value.done)
        assertTrue(vm.state.value.finished)
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("sentinel", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("onboarding_done", false))
    }
}
