package com.sentinel.app.home

import androidx.compose.ui.test.*
import com.sentinel.app.fakes.*
import com.sentinel.data.db.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApplication::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class HomeGuardAlertTest : ComposeAppTest() {
    @Test fun UT_AP_4_01_recent_disabled_rules_are_grouped_by_app() = runTest(main.dispatcher) {
        val now = data.clock.now()
        data.appsRows.value = listOf(data.app("com.a", "应用甲"), data.app("com.b", "应用乙"))
        data.overrideRows.value = listOf(
            RuleOverrideEntity("com.a", "r1", OverrideState.DISABLED, "应用崩溃", now - 1),
            RuleOverrideEntity("com.a", "r2", OverrideState.DISABLED, "应用崩溃", now - 2),
            RuleOverrideEntity("com.b", "r3", OverrideState.DISABLED, "重试风暴", now - 3),
            RuleOverrideEntity("com.a", "old", OverrideState.DISABLED, "旧记录", now - 86_400_001),
            RuleOverrideEntity("com.a", "pinned", OverrideState.PINNED, "已撤销", now - 4))
        val vm = keep(HomeViewModel(data.global, data.events, data.statuses, data.overrides,
            data.apps, data.clock, privateDnsWarning = { null })); watch(vm.state)
        assertEquals(setOf("com.a", "com.b"), vm.state.value.guardAlerts.map { it.pkg }.toSet())
        assertEquals(2, vm.state.value.guardAlerts.size)
        val a = vm.state.value.guardAlerts.single { it.pkg == "com.a" }
        assertEquals("应用甲", a.label)
        assertEquals(setOf("r1", "r2"), a.ruleIds.toSet())
        assertEquals(now - 1, a.at)
        assertEquals("应用崩溃", a.reason)
    }

    @Test fun UT_AP_4_02_undo_button_routes_to_guard_undo_for_the_alert() {
        val app = data.app()
        data.appsRows.value = listOf(app)
        data.overrideRows.value = listOf("r1", "r2").map {
            RuleOverrideEntity(app.pkg, it, OverrideState.DISABLED, "应用崩溃", data.clock.now()) }
        render()
        compose.onNodeWithTag("home:guard-undo:${app.pkg}").performClick()
        compose.waitForIdle()
        assertEquals(setOf("r1", "r2"), data.overrideRows.value.map { it.ruleId }.toSet())
        assertTrue(data.overrideRows.value.all { it.pkg == app.pkg && it.state == OverrideState.PINNED })
        assertEquals(setOf("r1", "r2"), data.calls.filter {
            it.dao == "RuleOverrideDao" && it.method == "remove" && it.args[0] == app.pkg
        }.map { it.args[1] }.toSet(), "GuardRunner.undo must remove then pin both rules")
    }

    @Test fun UT_AP_4_03_private_dns_and_shizuku_warnings_are_both_present() = runTest(main.dispatcher) {
        val privateDns = "私人 DNS 会让拦截失效"
        val shizuku = "Shizuku 未激活（不影响已生效项）"
        data.statusRows.value = listOf(EngineStatusEntity(EngineId.SYSTEM,
            EngineState.DEGRADED, shizuku, data.clock.now()))
        val vm = keep(HomeViewModel(data.global, data.events, data.statuses, data.overrides,
            data.apps, data.clock, privateDnsWarning = { privateDns })); watch(vm.state)
        assertContains(vm.state.value.warnings, privateDns)
        assertContains(vm.state.value.warnings, shizuku)
    }
}
