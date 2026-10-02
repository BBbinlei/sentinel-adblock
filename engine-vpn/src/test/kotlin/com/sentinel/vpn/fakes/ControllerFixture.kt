package com.sentinel.vpn.fakes

import com.sentinel.data.Clock
import com.sentinel.data.db.*
import com.sentinel.data.repo.EngineStatusRepository
import com.sentinel.vpn.decide.*
import com.sentinel.vpn.service.VpnController
import com.sentinel.vpn.tun.*
import java.io.Closeable
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.test.*

/** 所有未定的 controller 构造参数集中在此处，完整假设见 README A4。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ControllerFixture(val test: TestScope,
    statusFactory: (MemoryData) -> EngineStatusRepository = { it.status }) : Closeable {
    private val dispatcher = StandardTestDispatcher(test.testScheduler)
    private val job = SupervisorJob(test.backgroundScope.coroutineContext[Job])
    val scope = CoroutineScope(test.backgroundScope.coroutineContext + job + dispatcher)
    val clock = Clock { 100_000L + test.testScheduler.currentTime }
    val data = MemoryData(clock, scope.coroutineContext)
    val factory = FakeTunFactory()
    val upstream = FakeUpstream()
    val stops = AtomicInteger()
    var pkg: String? = "test.app"
    @Volatile var failDecisions = false
    val decisionFaults = AtomicInteger()
    val decisionFailures = Channel<Unit>(Channel.UNLIMITED)
    val contexts: MutableList<DnsContext> = Collections.synchronizedList(mutableListOf())
    lateinit var decisionSource: DecisionSource; private set
    val controller = VpnController(
        scope = scope, tunFactory = factory, selfPkg = "com.sentinel.adblock",
        apps = data.apps, global = data.global, overrides = data.overrides, rewards = data.rewards,
        ruleStore = data.rules, events = data.events, signals = data.signals, status = statusFactory(data),
        resolver = upstream, pkgs = PackageResolver { pkg }, clock = clock,
        stopService = { factory.operations.add("stopService"); stops.incrementAndGet() },
        decisionSourceDecorator = { delegate ->
            decisionSource = delegate
            object : DecisionSource {
                override fun matcher() = delegate.matcher()
                override fun context(pkg: String?): DnsContext {
                    if (failDecisions) { decisionFaults.incrementAndGet(); decisionFailures.trySend(Unit); throw IOException("injected decision-source failure") }
                    return delegate.context(pkg).also { contexts.add(it) }
                }
            }
        }
    )
    suspend fun start(installRules: Boolean = true) {
        if (installRules) data.initialize(rule(), rule("sdk.example.test", com.sentinel.rules.model.DomainTag.AD_SDK))
        else data.sql("INSERT OR IGNORE INTO global_state (id, enabled, pausedUntil, ruleVersion) VALUES (0, 1, NULL, 0)")
        data.app(); controller.start(); test.runCurrent()
    }
    fun tick(ms: Long) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(ms))
        test.advanceTimeBy(ms); test.runCurrent()
    }
    suspend fun query(name: String = "ads.example.test", id: Int = 0x1234): ByteArray {
        val result = exchange(factory.current, Wire.query(name, id = id)); test.runCurrent(); return result
    }
    suspend fun stop() { controller.stop("test cleanup"); test.runCurrent() }
    override fun close() { factory.close(); job.cancel(); test.runCurrent(); data.close() }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun TestScope.withController(
    statusFactory: (MemoryData) -> EngineStatusRepository = { it.status },
    block: suspend ControllerFixture.() -> Unit) {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try { ControllerFixture(this, statusFactory).use { fixture -> try { fixture.block() } finally { fixture.stop() } } }
    finally { Dispatchers.resetMain() }
}
