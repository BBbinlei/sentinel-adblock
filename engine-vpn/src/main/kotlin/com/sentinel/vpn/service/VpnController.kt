package com.sentinel.vpn.service

import android.os.SystemClock
import android.util.Log
import com.sentinel.data.Clock
import com.sentinel.data.db.*
import com.sentinel.data.policy.EffectivePolicy
import com.sentinel.data.repo.*
import com.sentinel.data.rules.RuleStore
import com.sentinel.rules.domain.DomainMatcher
import com.sentinel.vpn.decide.*
import com.sentinel.vpn.dns.UpstreamResolver
import com.sentinel.vpn.tun.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*

@OptIn(FlowPreview::class)
class VpnController(private val scope: CoroutineScope, private val tunFactory: TunFactory,
    private val selfPkg: String, private val apps: AppConfigRepository, private val global: GlobalStateRepository,
    private val overrides: OverrideRepository, private val rewards: RewardWindowRepository,
    private val ruleStore: RuleStore, events: EventRepository, private val signals: SignalRepository,
    private val status: EngineStatusRepository, private val resolver: UpstreamResolver,
    private val pkgs: PackageResolver, private val clock: Clock, private val stopService: () -> Unit,
    decisionSourceDecorator: (DecisionSource) -> DecisionSource = { it }) {
    private val mutableState = MutableStateFlow(EngineState.NOT_SETUP)
    val state: StateFlow<EngineState> = mutableState.asStateFlow()
    private val lifecycle = Mutex()
    @Volatile private var active = false
    @Volatile private var rules: DomainMatcher? = null
    @Volatile private var configs = emptyMap<String, AppConfigEntity>()
    @Volatile private var globalState = GlobalStateEntity(enabled = false)
    @Volatile private var disabled = emptyMap<String, Set<String>>()
    // 奖励窗口与重试风暴的计时用单调时钟（不受墙钟调整影响）：窗口到期时刻在读到时换算成单调时间。
    private data class Window(val until: Long, val expiresAt: Long)
    @Volatile private var windows = emptyMap<String, Window>()
    private val watchers = mutableListOf<Job>()
    private var handle: TunHandle? = null
    private var loop: Job? = null
    private var spec: TunSpec? = null
    private val batcher = EventBatcher(events, scope = scope, clock = clock)
    private val storms = RetryStormDetector(SystemClock::elapsedRealtime)
    private val decisions = decisionSourceDecorator(object : DecisionSource {
        override fun matcher() = rules
        override fun context(pkg: String?): DnsContext = DnsContext(
            EffectivePolicy.resolve(configs[pkg], pkg.orEmpty(), globalState, clock.now()),
            disabled[pkg].orEmpty(), (windows[pkg]?.expiresAt ?: Long.MIN_VALUE) > SystemClock.elapsedRealtime())
    })
    private fun applyWindows(open: Map<String, Long>) {
        val now = clock.now(); val mono = SystemClock.elapsedRealtime(); val old = windows
        windows = open.mapValues { (pkg, until) ->
            old[pkg]?.takeIf { it.until == until } ?: Window(until, mono + (until - now))
        }
    }
    private suspend fun report(state: EngineState, message: String? = null) {
        try { status.report(EngineId.VPN, state, message) }
        catch (e: Exception) { currentCoroutineContext().ensureActive(); Log.w("SentinelVpn", "状态写入失败", e) }
        mutableState.value = state
    }
    private fun onEvent(e: LoopEvent) {
        batcher.offer(e)
        if (e is LoopEvent.Dns && e.decision.verdict == DnsVerdict.BLOCK && !e.pkg.isNullOrBlank()) {
            val rule = e.decision.hit?.ruleId ?: return
            if (storms.record(e.pkg, rule)) scope.launch {
                try { signals.emit(e.pkg, SignalKind.RETRY_STORM, rule) }
                catch (error: Exception) { currentCoroutineContext().ensureActive(); Log.w("SentinelVpn", "信号写入失败", error) }
            }
        }
    }
    private suspend fun replace(excluded: Set<String>): Boolean {
        val next = TunSpec.build(selfPkg, excluded)
        if (spec == next) return true
        val new = tunFactory.establish(next) ?: return false
        val old = handle; val oldLoop = loop
        handle = new; spec = next
        loop = scope.launch(Dispatchers.IO) { PacketLoop(new.input, new.output, resolver, decisions, pkgs, ::onEvent).run() }
        old?.close(); oldLoop?.cancel()
        return true
    }
    suspend fun start() = lifecycle.withLock {
        if (active) return@withLock
        globalState = global.get(); configs = apps.observeAll().first().associateBy { it.pkg }
        disabled = overrides.observeDisabled().first()
        try { applyWindows(rewards.observeOpen().first()) } catch (e: Exception) {
            currentCoroutineContext().ensureActive(); Log.w("SentinelVpn", "奖励窗口读取失败，按关闭处理", e); windows = emptyMap()
        }
        rules = withContext(Dispatchers.IO) { ruleStore.loadDomainMatcher() }
        active = true
        if (!replace(apps.observeExcluded().first())) {
            active = false; report(EngineState.NOT_SETUP); stopService(); return@withLock
        }
        report(EngineState.RUNNING)
        fun <T> watch(flow: Flow<T>, update: suspend (T) -> Unit) {
            watchers += scope.launch {
                try { flow.collect { update(it) } }
                catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.w("SentinelVpn", "配置订阅失败", e)
                    stop("配置读取失败，网络已恢复直连")
                }
            }
        }
        watch(apps.observeAll()) { configs = it.associateBy { cfg -> cfg.pkg } }
        watch(global.observe()) { globalState = it }
        watch(overrides.observeDisabled()) { disabled = it }
        watchers += scope.launch {
            rewards.observeOpen().catch {
                windows = emptyMap(); Log.w("SentinelVpn", "奖励窗口读取失败，按关闭处理", it)
            }.collect { applyWindows(it) }
        }
        watch(global.observe().map { it.ruleVersion }.distinctUntilChanged().drop(1)) {
            rules = withContext(Dispatchers.IO) { ruleStore.loadDomainMatcher() }
        }
        watch(apps.observeExcluded().debounce(2000)) {
            lifecycle.withLock {
                if (active && !replace(it)) { Log.w("SentinelVpn", "TUN 重建未获授权") }
            }
        }
    }
    suspend fun stop(reason: String) = withContext(NonCancellable) {
        lifecycle.withLock {
            active = false
            handle?.close(); handle = null; spec = null
            loop?.cancel(); loop = null
            watchers.forEach { it.cancel() }; watchers.clear()
            batcher.close()
            report(EngineState.STOPPED, reason)
            stopService()
        }
    }
}
