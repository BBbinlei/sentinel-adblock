package com.sentinel.app.system

import com.sentinel.app.fakes.*
import com.sentinel.system.ops.OpStatus
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
class SystemCleanupViewModelTest : AppTest() {
    @Test fun UT_AP_4_04_unverified_cannot_auto_apply() = runTest(main.dispatcher) {
        val system = FakeSystem(data)
        val vm = keep(SystemCleanupViewModel(system.profile, system.executor)); watch(vm.state)
        assertFalse(vm.state.value.single { it.op.id == "unverified" }.canAuto)
        assertTrue(vm.state.value.single { it.op.id == "normal" }.canAuto)
    }

    @Test fun UT_AP_4_05_apply_all_skips_optional_uninstall_and_unverified() = runTest(main.dispatcher) {
        val system = FakeSystem(data)
        val vm = keep(SystemCleanupViewModel(system.profile, system.executor)); watch(vm.state)
        vm.applyAll(); runCurrent()
        assertEquals(listOf("apply normal"), system.shell.commands.filter { it.startsWith("apply ") })
        assertEquals(listOf("normal"), data.logRows.value.filter { it.success }.map { it.opId })
        assertEquals(OpStatus.APPLIED, vm.state.value.single { it.op.id == "normal" }.status)
        assertEquals(OpStatus.NOT_APPLIED, vm.state.value.single { it.op.id == "optional" }.status)
        assertEquals(OpStatus.NOT_APPLIED, vm.state.value.single { it.op.id == "uninstall" }.status)
    }
}
