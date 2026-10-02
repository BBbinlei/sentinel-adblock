package com.sentinel.a11y.di

import android.content.Context
import android.util.Log
import com.sentinel.a11y.core.A11yState
import com.sentinel.data.Clock
import com.sentinel.data.db.EffectiveConfig
import com.sentinel.data.db.ProtectLevel
import com.sentinel.data.db.RewardedMode
import com.sentinel.data.db.RuleOrigin
import com.sentinel.data.repo.*
import com.sentinel.data.rules.RuleStore
import com.sentinel.data.rules.SubscriptionUpdater
import com.sentinel.rules.model.UiRule
import com.sentinel.rules.ui.BuiltInUiRules
import com.sentinel.rules.ui.UiRuleIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.ConcurrentHashMap

val a11yModule = module {
    single {
        A11yRuntime(androidContext(), appConfigs = get(), overrides = get(), jumpExceptions = get(),
            rewardWindows = get(), events = get(), signals = get(), statuses = get(), users = get(),
            store = get(), updater = get(), clock = get())
    }
}

/** 数据层与无障碍服务之间的快照缓存与写入入口（服务进程内唯一）。 */
class A11yRuntime(val context: Context, val appConfigs: AppConfigRepository, private val overrides: OverrideRepository,
                  private val jumpExceptions: JumpExceptionRepository, val rewardWindows: RewardWindowRepository,
                  val events: EventRepository, val signals: SignalRepository, val statuses: EngineStatusRepository,
                  private val users: UserRuleRepository, private val store: RuleStore,
                  private val updater: SubscriptionUpdater, private val clock: Clock) {
    private class Cached<T>(val value: T, val at: Long)

    @Volatile private var index: UiRuleIndex = UiRuleIndex.build(BuiltInUiRules.all)
    @Volatile var labels: Map<String, String> = emptyMap()   // 应用名 -> 包名
    private val configs = ConcurrentHashMap<String, Cached<EffectiveConfig>>()
    private val disabledRules = ConcurrentHashMap<String, Set<String>>()
    private val exceptions = ConcurrentHashMap<Pair<String, String>, Cached<Boolean>>()
    private val temporaryExceptions = ConcurrentHashMap.newKeySet<Pair<String, String>>()

    val state = object : A11yState {
        // 读不到数据时按更宽松的一侧处理：关闭防护、不停用规则、无例外。
        override fun cfg(pkg: String): EffectiveConfig =
            configs[pkg]?.value ?: EffectiveConfig(pkg, ProtectLevel.OFF, false, false,
                RewardedMode.BLOCK, false, false, false, false, false)
        override fun disabled(pkg: String): Set<String> = disabledRules[pkg].orEmpty()
        override fun excepted(src: String, tgt: String): Boolean =
            (src to tgt) in temporaryExceptions || exceptions[src to tgt]?.value == true
        override fun index(): UiRuleIndex = index
    }

    suspend fun reloadIndex() = withContext(Dispatchers.IO) { index = store.loadUiIndex() }

    /** 在把事件交给 A11yBrain 之前，为用到的包准备好同步可读的配置快照。 */
    suspend fun prefetch(pkgs: Collection<String>, pair: Pair<String, String>? = null) = withContext(Dispatchers.IO) {
        val now = clock.now()
        var disabledAll: Map<String, Set<String>>? = null
        for (pkg in pkgs.toSet()) {
            val c = configs[pkg]
            if (c == null || now - c.at > TTL_MS) {
                try {
                    configs[pkg] = Cached(appConfigs.effective(pkg), now)
                    val all = disabledAll ?: overrides.observeDisabled().first().also { disabledAll = it }
                    disabledRules[pkg] = all[pkg].orEmpty()
                } catch (e: Exception) { Log.w(TAG, "读取配置失败: $pkg", e) }
            }
        }
        if (pair != null) {
            val e = exceptions[pair]
            if (e == null || now - e.at > TTL_MS) {
                try { exceptions[pair] = Cached(jumpExceptions.isExcepted(pair.first, pair.second), now) }
                catch (ex: Exception) { Log.w(TAG, "读取跳转例外失败", ex) }
            }
        }
    }

    suspend fun addException(source: String, target: String) {
        // 先放行本次恢复；写库失败或缓存刷新都不能撤销用户在本进程内的选择。
        temporaryExceptions += source to target
        jumpExceptions.add(source, target)
        exceptions[source to target] = Cached(true, clock.now())
    }

    suspend fun acceptLearned(rule: UiRule) {
        users.add(rule, RuleOrigin.LEARNED)
        updater.rebuildFromCache()
        reloadIndex()
    }

    companion object {
        const val TAG = "SentinelA11y"
        const val TTL_MS = 2_000L
    }
}
