package com.sentinel.vpn.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.sentinel.data.Clock
import com.sentinel.rules.model.EventKind
import com.sentinel.vpn.decide.*
import com.sentinel.vpn.fakes.*
import com.sentinel.vpn.tun.LoopEvent
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.robolectric.shadows.ShadowSystemClock
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class EventBatcherTest {
    @Test fun `UT-VP-5-05 two-second batches and per-pair minute throttle`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { MemoryData(Clock { 100_000 + testScheduler.currentTime }, coroutineContext).use { data ->
            val batcher = EventBatcher(data.events)
            val blocked = DnsDecision(DnsVerdict.BLOCK, matcher(rule()).lookup("ads.example.test"))
            val would = DnsDecision(DnsVerdict.WOULD_BLOCK, blocked.hit)
            fun tick(ms: Long) { ShadowSystemClock.advanceBy(Duration.ofMillis(ms)); advanceTimeBy(ms); runCurrent() }
            batcher.offer(LoopEvent.Dns("test.app", "a.ads.example.test", blocked))
            batcher.offer(LoopEvent.Dns("test.app", "b.ads.example.test", would))
            repeat(10) { batcher.offer(LoopEvent.HttpDnsRejected("test.app", "203.107.1.33")) }
            batcher.offer(LoopEvent.HttpDnsRejected("other.app", "203.107.1.33"))
            batcher.offer(LoopEvent.HttpDnsRejected("test.app", "203.107.1.34")); runCurrent()
            tick(1999); assertTrue(data.eventRows().isEmpty()); tick(1)
            val first = data.eventRows(); assertEquals(5, first.size)
            assertEquals(1, first.count { it.kind == EventKind.DNS_BLOCKED }); assertEquals(1, first.count { it.kind == EventKind.WOULD_BLOCK })
            assertEquals(3, first.count { it.kind == EventKind.HTTPDNS_REJECTED })
            batcher.offer(LoopEvent.HttpDnsRejected("test.app", "203.107.1.33")); tick(2000)
            assertEquals(5, data.eventRows().size)
            tick(56_000) // 从首次 offer 起精确 60 秒。
            batcher.offer(LoopEvent.HttpDnsRejected("test.app", "203.107.1.33")); tick(2000)
            assertEquals(2, data.eventRows().count { it.kind == EventKind.HTTPDNS_REJECTED && it.pkg == "test.app" && it.detail == "203.107.1.33" })
        } } finally { Dispatchers.resetMain() }
    }
}
