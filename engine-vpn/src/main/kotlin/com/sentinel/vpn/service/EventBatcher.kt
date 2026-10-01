package com.sentinel.vpn.service

import android.os.SystemClock
import android.util.Log
import com.sentinel.data.Clock
import com.sentinel.data.repo.EventRepository
import com.sentinel.rules.model.EventKind
import com.sentinel.vpn.decide.DnsVerdict
import com.sentinel.vpn.tun.LoopEvent
import kotlinx.coroutines.*

class EventBatcher(private val events: EventRepository, private val intervalMs: Long = 2_000,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val clock: Clock = Clock(SystemClock::elapsedRealtime)) {
    private val pending = ArrayDeque<LoopEvent>()
    private val lastHttpDns = mutableMapOf<Pair<String?, String>, Long>()
    private var timer: Job? = null
    private var closed = false
    init { require(intervalMs > 0) }
    @Synchronized fun offer(e: LoopEvent) {
        if (closed || e is LoopEvent.Dns && e.decision.verdict == DnsVerdict.FORWARD) return
        if (e is LoopEvent.HttpDnsRejected) {
            val now = clock.now(); val key = e.pkg to e.ip
            lastHttpDns.entries.removeAll { now - it.value >= 60_000 }
            if (key in lastHttpDns) return
            lastHttpDns[key] = now
        }
        // ponytail: bounded best-effort telemetry; use a disk queue if lossless logging is required.
        if (pending.size == 2000) pending.removeFirst()
        pending.addLast(e)
        if (timer == null) timer = scope.launch {
            delay(intervalMs)
            val batch = synchronized(this@EventBatcher) { pending.toList().also { pending.clear(); timer = null } }
            write(batch)
        }
    }
    private suspend fun write(batch: List<LoopEvent>) {
        for (e in batch) try {
            when (e) {
                is LoopEvent.Dns -> events.log(e.pkg,
                    if (e.decision.verdict == DnsVerdict.BLOCK) EventKind.DNS_BLOCKED else EventKind.WOULD_BLOCK,
                    e.decision.hit?.ruleId, e.domain)
                is LoopEvent.HttpDnsRejected -> events.log(e.pkg, EventKind.HTTPDNS_REJECTED, null, e.ip)
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.w("SentinelVpn", "事件写入失败", e)
        }
    }
    suspend fun close() {
        val batch = synchronized(this) { closed = true; timer?.cancel(); timer = null; pending.toList().also { pending.clear() } }
        write(batch)
    }
}
