package com.sentinel.app.rules

import com.sentinel.app.fakes.*
import com.sentinel.data.db.*
import com.sentinel.data.rules.UpdateReport
import com.sentinel.rules.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class RulesViewModelTest : AppTest() {
    private val subscriptions = MutableStateFlow<List<SubscriptionEntity>>(emptyList())
    private val saved = mutableListOf<SubscriptionEntity>()
    private fun vm(update: suspend () -> UpdateReport = { UpdateReport(0, 0, 0) },
        rebuild: suspend () -> Long = { 1L }) = keep(RulesViewModel(
        subscriptions = subscriptions, userRules = data.users,
        saveSubscription = { saved += it; subscriptions.value += it },
        updateAll = update, rebuildFromCache = rebuild))

    @Test fun UT_AP_6_01_only_https_is_saved() = runTest(main.dispatcher) {
        val vm = vm(); watch(vm.state)
        for (url in listOf("http://rules.example/hosts", "ftp://rules.example/hosts",
            "file:///hosts", "rules.example/hosts", "https://", "httpsx://rules.example/hosts")) {
            vm.add("测试", url, SubscriptionFormat.HOSTS); runCurrent()
            assertTrue(saved.isEmpty(), "Rejected URL must never reach saveSubscription: $url")
        }
        vm.add("安全订阅", "https://rules.example/hosts", SubscriptionFormat.HOSTS); runCurrent()
        assertEquals(1, saved.size)
        assertEquals("https://rules.example/hosts", saved.single().url)
        assertEquals("安全订阅", saved.single().name)
        assertEquals(SubscriptionFormat.HOSTS, saved.single().format)
    }

    @Test fun UT_AP_6_02_updating_stays_true_until_report_then_shows_counts() = runTest(main.dispatcher) {
        val report = CompletableDeferred<UpdateReport>()
        var updates = 0
        val vm = vm(update = { updates++; report.await() }); watch(vm.state)
        assertFalse(vm.state.value.updating)
        vm.updateNow(); runCurrent()
        assertEquals(1, updates)
        assertTrue(vm.state.value.updating)
        report.complete(UpdateReport(updated = 2, failed = 1, ruleVersion = 7)); runCurrent()
        assertFalse(vm.state.value.updating)
        assertEquals("更新 2 个，失败 1 个", vm.state.value.message)
    }

    @Test fun UT_AP_6_03_delete_user_rule_rebuilds_after_deletion() = runTest(main.dispatcher) {
        val rule = NotifyRule("user:marketing", "com.example.shop", "marketing", emptyList(), "test")
        data.users.add(rule, RuleOrigin.LEARNED)
        var rebuilds = 0
        val vm = vm(rebuild = {
            assertTrue(data.userRows.value.isEmpty(), "Rebuild must see the deleted rule removed")
            rebuilds++; 9L
        }); watch(vm.state)
        vm.deleteUserRule(rule.id); runCurrent()
        assertTrue(data.userRows.value.isEmpty())
        assertEquals(1, rebuilds)
        assertEquals(listOf(rule.id), data.calls.filter {
            it.dao == "UserRuleDao" && it.method == "delete"
        }.map { it.args.single() })
    }
}
