package com.sentinel.notify.di

import android.content.Context
import com.sentinel.data.Clock
import com.sentinel.data.db.*
import com.sentinel.data.repo.*
import com.sentinel.data.rules.RuleStore
import com.sentinel.data.rules.SubscriptionUpdater
import com.sentinel.notify.core.*
import com.sentinel.rules.model.NotifyRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val notifyModule = module {
    single<LearnerStore> {
        val prefs = androidContext().getSharedPreferences("sentinel-notify", Context.MODE_PRIVATE)
        object : LearnerStore {
            override fun load(): String? = prefs.getString("learner", null)
            override fun save(json: String) {
                check(prefs.edit().putString("learner", json).commit()) { "通知学习记录保存失败" }
            }
        }
    }
    single { DismissLearner(get()) { get<Clock>().now() } }
    single { NotifyRuntime(selfPkg = androidContext().packageName, apps = get(), overrides = get(),
        store = get(), learner = get(), events = get(), statuses = get(), users = get(), updater = get()) }
}

class NotifyRuntime(private val selfPkg: String, private val apps: AppConfigRepository,
    private val overrides: OverrideRepository, private val store: RuleStore,
    private val learner: DismissLearner, val events: EventRepository,
    val statuses: EngineStatusRepository, private val users: UserRuleRepository,
    private val updater: SubscriptionUpdater) {
    @Volatile private var rules = BuiltInNotifyRules.all

    suspend fun reloadRules() = withContext(Dispatchers.IO) {
        rules = BuiltInNotifyRules.all + store.loadNotifyRules()
    }

    suspend fun engineFor(pkg: String): NotifyEngine {
        val cfg = apps.effective(pkg)
        val disabled = overrides.observeDisabled().first()[pkg].orEmpty()
        val currentRules = rules
        return NotifyEngine(selfPkg, object : NotifyState {
            override fun cfg(pkg: String): EffectiveConfig = cfg
            override fun disabled(pkg: String): Set<String> = disabled
            override fun rules(): List<NotifyRule> = currentRules
        }, learner)
    }

    suspend fun accept(rule: NotifyRule) {
        users.add(rule, RuleOrigin.LEARNED)
        updater.rebuildFromCache()
        reloadRules()
    }
}
