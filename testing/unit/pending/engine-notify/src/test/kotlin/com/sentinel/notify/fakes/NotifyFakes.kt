package com.sentinel.notify.fakes

import com.sentinel.data.db.ProtectLevel
import com.sentinel.data.db.RewardedMode
import com.sentinel.data.db.RuleOrigin
import com.sentinel.data.policy.EffectiveConfig
import com.sentinel.notify.core.BuiltInNotifyRules
import com.sentinel.notify.core.LearnerStore
import com.sentinel.notify.core.NotifyState
import com.sentinel.rules.model.EventKind
import com.sentinel.rules.model.NotifyRule
import com.sentinel.rules.model.Rule

const val SELF_PKG = "com.sentinel.adblock"
const val MARKET_PKG = "com.heytap.market"
const val DAY_MS = 86_400_000L

fun config(pkg: String, level: ProtectLevel = ProtectLevel.STANDARD, notify: Boolean = true) =
    EffectiveConfig(
        pkg = pkg, level = level, observing = false, splash = true,
        rewarded = RewardedMode.SILENT, shake = true, jumpBack = true,
        notify = notify, limitOverlay = false, denyClipboard = false,
    )

class FakeClock(var now: Long = 100 * DAY_MS) {
    fun advance(ms: Long) { now += ms }
}

class MemoryLearnerStore : LearnerStore {
    private var json: String? = null
    override fun load(): String? = json
    override fun save(json: String) { this.json = json }
}

class FakeNotifyState : NotifyState {
    val configs = mutableMapOf<String, EffectiveConfig>()
    val disabledRules = mutableMapOf<String, Set<String>>()
    var userRules: List<NotifyRule> = emptyList()
    override fun cfg(pkg: String): EffectiveConfig = configs[pkg] ?: config(pkg)
    override fun disabled(pkg: String): Set<String> = disabledRules[pkg].orEmpty()
    override fun rules(): List<NotifyRule> = BuiltInNotifyRules.all + userRules
}

// data 的仓库是 concrete class，PLAN 没有 DAO 签名；这些是假邻居，未继承产品仓库。
class FakeUserRuleRepository {
    val added = mutableListOf<Pair<Rule, RuleOrigin>>()
    suspend fun add(rule: Rule, origin: RuleOrigin) { added += rule to origin }
}

class FakeSubscriptionUpdater(
    private val users: FakeUserRuleRepository,
    private val state: FakeNotifyState,
) {
    var rebuildCalls = 0
        private set
    suspend fun rebuildFromCache(): Long {
        rebuildCalls++
        state.userRules = users.added.map { it.first }.filterIsInstance<NotifyRule>()
        return rebuildCalls.toLong()
    }
}

class FakeEventRepository {
    data class RecordedEvent(val pkg: String?, val kind: EventKind, val ruleId: String?, val detail: String?)
    val events = mutableListOf<RecordedEvent>()
    suspend fun log(pkg: String?, kind: EventKind, ruleId: String?, detail: String? = null) {
        events += RecordedEvent(pkg, kind, ruleId, detail)
    }
}
