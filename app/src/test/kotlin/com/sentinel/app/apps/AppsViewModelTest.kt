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
class AppsViewModelTest : AppTest() {
    @Test fun UT_AP_5_01_sort_by_seven_day_counts_and_search_both_fields() = runTest(main.dispatcher) {
        val reader = data.app("com.example.reader", "阅读器")
        val video = data.app("com.example.video", "视频")
        data.appsRows.value = listOf(reader, video)
        val now = data.clock.now()
        data.eventRows.value = listOf(
            EventEntity(1, now - 1, reader.pkg, EventKind.DNS_BLOCKED, "dns:a.cn"),
            EventEntity(2, now - 2, video.pkg, EventKind.DNS_BLOCKED, "dns:b.cn"),
            EventEntity(3, now - 3, video.pkg, EventKind.POPUP_CLOSED, "popup"),
            EventEntity(4, now - 8 * 86_400_000L, reader.pkg, EventKind.DNS_BLOCKED, "old")
        )
        val vm = keep(AppsViewModel(data.apps, data.events, data.clock)); watch(vm.state)
        assertEquals(listOf(video.pkg, reader.pkg), vm.state.value.map { it.pkg })
        assertEquals(listOf(2, 1), vm.state.value.map { it.blocked7d })
        vm.search("阅读"); runCurrent()
        assertEquals(listOf(reader.pkg), vm.state.value.map { it.pkg })
        vm.search("example.video"); runCurrent()
        assertEquals(listOf(video.pkg), vm.state.value.map { it.pkg })
        vm.search("不存在"); runCurrent(); assertTrue(vm.state.value.isEmpty())
        vm.search(""); runCurrent()
        assertEquals(listOf(video.pkg, reader.pkg), vm.state.value.map { it.pkg })
    }

    @Test fun UT_AP_5_02_sensitive_app_is_default_allowed() = runTest(main.dispatcher) {
        data.appsRows.value = listOf(data.app("com.example.bank", "某某银行", sensitive = true), data.app())
        val vm = keep(AppsViewModel(data.apps, data.events, data.clock)); watch(vm.state)
        val bank = vm.state.value.single { it.pkg == "com.example.bank" }
        assertTrue(bank.defaultAllowed)
        assertEquals(ProtectLevel.OFF, bank.level)
        assertFalse(vm.state.value.single { it.pkg == "com.example.reader" }.defaultAllowed)
    }
}
