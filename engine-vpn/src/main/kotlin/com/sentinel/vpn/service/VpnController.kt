package com.sentinel.vpn.service

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
    private companion object {
        const val UNEXPECTED_STOP = "网络拦截意外停止，网络已恢复直连"
        const val REVOKED = "VPN 授权被撤销或被其他 VPN 取代"
        const val RULES_UNAVAILABLE = "规则不可用，暂停网络拦截"
        const val UPSTREAM_UNAVAILABLE = "DNS 上游连续不可用，网络拦截已停止，网络已恢复直连"
        // 连续三次 DoH 与 UDP 都失败才停止，容忍单次网络切换；有效应答重置计数。
        const val UPSTREAM_FAILURE_LIMIT = 3
    }
    private val mutableState = MutableStateFlow(EngineState.NOT_SETUP)
    val state: StateFlow<EngineState> = mutableState.asStateFlow()
    private val lifecycle = Mutex()
    // 只保护同步资源操作；这里绝不等待 Room 或其他挂起操作。
    private val tunLock = Any()
    @Volatile private var closed = false
    @Volatile private var stopReason: String? = null
    @Volatile private var active = false
    private var upstreamFailures = 0
    @Volatile private var rules: DomainMatcher? = null
    @Volatile private var configs = emptyMap<String, AppConfigEntity>()
    @Volatile private var globalState = GlobalStateEntity(enabled = false)
    @Volatile private var disabled = emptyMap<String, Set<String>>()
    @Volatile private var windows = emptyMap<String, Long>()
    private val watchers = mutableListOf<Job>()
    @Volatile private var handle: TunHandle? = null
    private var loop: Job? = null
    private var spec: TunSpec? = null
    private val batcher = EventBatcher(events, scope = scope, clock = clock)
    private val storms = RetryStormDetector(clock::now)
    private val decisions = decisionSourceDecorator(object : DecisionSource {
        override fun matcher() = rules
        override fun context(pkg: String?): DnsContext = DnsContext(
            EffectivePolicy.resolve(configs[pkg], pkg.orEmpty(), globalState, clock.now()),
            disabled[pkg].orEmpty(), (windows[pkg] ?: 0) > clock.now())
    })
    private suspend fun report(state: EngineState, message: String? = null) {
        try { status.report(EngineId.VPN, state, message) }
        catch (e: Exception) { currentCoroutineContext().ensureActive(); Log.w("SentinelVpn", "状态写入失败", e) }
        mutableState.value = state
    }
    @Volatile private var privateDnsWarning: String? = null
    private suspend fun reportHealth() {
        val warning = if (rules == null) RULES_UNAVAILABLE else privateDnsWarning
        if (warning != null) report(EngineState.DEGRADED, warning) else report(EngineState.RUNNING)
    }
    /** 系统「私人 DNS」警告变化（null 表示恢复）；只在运行中改变上报状态。 */
    suspend fun onPrivateDns(warning: String?) = lifecycle.withLock {
        privateDnsWarning = warning
        if (active) reportHealth()
    }
    @Synchronized fun onUpstreamTransport(success: Boolean) {
        if (!active) return
        upstreamFailures = if (success) 0 else upstreamFailures + 1
        if (upstreamFailures == UPSTREAM_FAILURE_LIMIT) {
            closeTun(UPSTREAM_UNAVAILABLE)
            scope.launch { stop(UPSTREAM_UNAVAILABLE) }
        }
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
    private fun replace(excluded: Set<String>): Boolean = synchronized(tunLock) {
        if (closed) return false
        val next = TunSpec.build(selfPkg, excluded)
        if (spec == next) return true
        val new = tunFactory.establish(next) ?: return false
        val old = handle; val oldLoop = loop
        handle = new; spec = next
        loop = scope.launch(Dispatchers.IO) {
            val failure = try { PacketLoop(new.input, new.output, resolver, decisions, pkgs, ::onEvent).run(); null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w("SentinelVpn", "报文循环异常", e); e }
            // 被替换或主动停止时 handle 已不是它，不算意外。
            if (handle === new && active) stop(UNEXPECTED_STOP)
            else if (failure != null) Log.w("SentinelVpn", "旧报文循环结束", failure)
        }
        old?.close(); oldLoop?.cancel()
        return true
    }
    suspend fun start() = lifecycle.withLock {
        if (active || closed) return@withLock
        globalState = global.get(); configs = apps.observeAll().first().associateBy { it.pkg }
        disabled = overrides.observeDisabled().first()
        try { windows = rewards.observeOpen().first() } catch (e: Exception) {
            currentCoroutineContext().ensureActive(); Log.w("SentinelVpn", "奖励窗口读取失败，按关闭处理", e); windows = emptyMap()
        }
        rules = loadRules()
        synchronized(tunLock) {
            if (closed) return@withLock
            active = true
        }
        if (!replace(apps.observeExcluded().first())) {
            if (closed) return@withLock
            active = false; report(EngineState.NOT_SETUP); stopService(); return@withLock
        }
        reportHealth()
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
            }.collect { windows = it }
        }
        watch(global.observe().map { it.ruleVersion }.distinctUntilChanged().drop(1)) {
            rules = loadRules()
            if (active) reportHealth()
        }
        // observeExcluded 内含每 60s 的 ticks（用于暂停/临时放行到期）；放在 Unconfined 上游，
        // 让这个周期定时器走真实时间，不占用调用方调度器，下游防抖仍在 scope 上。
        watch(apps.observeExcluded().flowOn(Dispatchers.Unconfined).debounce(2000)) {
            lifecycle.withLock {
                if (active && !replace(it)) { Log.w("SentinelVpn", "TUN 重建未获授权") }
            }
        }
    }
    private suspend fun loadRules(): DomainMatcher? = try { withContext(Dispatchers.IO) { ruleStore.loadDomainMatcher() } }
    catch (e: Exception) { currentCoroutineContext().ensureActive(); Log.w("SentinelVpn", "规则加载失败", e); null }
    /** 系统撤销 VPN 授权（VpnService.onRevoke）；关闭 TUN 并上报原因。 */
    fun onRevoked() { closeTun(REVOKED); scope.launch { stop(REVOKED) } }
    /** 不等待 lifecycle；销毁时先恢复网络，再异步写库。返回关闭前是否仍在运行。 */
    fun closeTun(reason: String): Boolean = synchronized(tunLock) {
        val wasActive = active
        if (stopReason == null) stopReason = reason
        closed = true
        active = false
        val old = handle
        handle = null; spec = null
        try { old?.close() }
        catch (e: Exception) { Log.w("SentinelVpn", "TUN 关闭失败", e) }
        finally { loop?.cancel(); loop = null }
        wasActive
    }
    suspend fun stop(reason: String) {
        closeTun(reason)
        withContext(NonCancellable) { lifecycle.withLock {
            watchers.forEach { it.cancel() }; watchers.clear()
            batcher.close()
            report(EngineState.STOPPED, stopReason)
            stopService()
        } }
    }
}
