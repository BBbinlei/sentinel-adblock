package com.sentinel.app.apps

import com.sentinel.app.fakes.*
import com.sentinel.data.db.*
import com.sentinel.rules.model.EventKind
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
class AppDetailViewModelTest : AppTest() {
    private val pkg = "com.example.reader"
    private fun vm() = keep(AppDetailViewModel(pkg, data.apps, data.events, data.clock))

    @Test fun UT_AP_5_03_each_level_is_written_to_repository() = runTest(main.dispatcher) {
        data.appsRows.value = listOf(data.app(pkg))
        val vm = vm(); watch(vm.state)
        ProtectLevel.entries.forEach { level ->
            vm.setLevel(level); runCurrent()
            assertEquals(level, data.appsRows.value.single().level)
            assertEquals(level, vm.state.value.level)
        }
        assertEquals(ProtectLevel.entries.toList(), data.calls.filter {
            it.dao == "AppConfigDao" && it.method in setOf("upsert", "update")
        }.map { (it.args.single() as AppConfigEntity).level })
    }

    @Test fun UT_AP_5_04_observation_days_round_up() = runTest(main.dispatcher) {
        for ((remaining, days) in listOf(1L to 1, 86_400_000L to 1, 86_400_001L to 2, 259_200_000L to 3)) {
            data.appsRows.value = listOf(data.app(pkg, observationEndsAt = data.clock.now() + remaining))
            val vm = vm(); watch(vm.state)
            assertEquals(days, vm.state.value.observingDaysLeft, "remaining=$remaining")
        }
        data.appsRows.value = listOf(data.app(pkg, observationEndsAt = 0))
        val vm = vm(); watch(vm.state); assertNull(vm.state.value.observingDaysLeft)
    }

    @Test fun UT_AP_5_05_would_block_is_recent_details_for_this_package_deduplicated() = runTest(main.dispatcher) {
        data.appsRows.value = listOf(data.app(pkg))
        val now = data.clock.now()
        data.eventRows.value = listOf(
            EventEntity(1, now - 1, pkg, EventKind.WOULD_BLOCK, "dns:ads.cn", "a.ads.cn"),
            EventEntity(2, now - 2, pkg, EventKind.WOULD_BLOCK, "dns:ads.cn", "a.ads.cn"),
            EventEntity(3, now - 3, pkg, EventKind.WOULD_BLOCK, "dns:b.cn", "b.cn"),
            EventEntity(4, now - 3 * 86_400_000L - 1, pkg, EventKind.WOULD_BLOCK, "dns:old.cn", "old.cn"),
            EventEntity(5, now - 4, "com.other", EventKind.WOULD_BLOCK, "dns:other.cn", "other.cn"),
            EventEntity(6, now - 5, pkg, EventKind.DNS_BLOCKED, "dns:blocked.cn", "blocked.cn"),
            EventEntity(7, now - 6, pkg, EventKind.WOULD_BLOCK, "dns:empty.cn", null)
        )
        val vm = vm(); watch(vm.state)
        assertEquals(setOf("a.ads.cn", "b.cn"), vm.state.value.wouldBlock.toSet())
        assertEquals(2, vm.state.value.wouldBlock.size)
    }
}
