package com.sentinel.data.repo

import androidx.room.withTransaction
import com.sentinel.data.Clock
import com.sentinel.data.contract.DataContract
import com.sentinel.data.db.*
import com.sentinel.data.policy.EffectivePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

class AppConfigRepository(private val db: SentinelDatabase, private val clock: Clock,
    private val global: GlobalStateRepository, private val signals: SignalRepository) {
    private val dao = db.appConfigDao()
    fun observeAll(): Flow<List<AppConfigEntity>> = dao.observeAll()
    fun observe(pkg: String): Flow<AppConfigEntity?> = dao.observe(pkg)
    suspend fun effective(pkg: String): EffectiveConfig = EffectivePolicy.resolve(dao.get(pkg), pkg, global.get(), clock.now())
    private fun ticks(): Flow<Unit> = flow { while (true) { emit(Unit); delay(DataContract.REFRESH_MS) } }
    fun observeEffective(pkg: String): Flow<EffectiveConfig> = combine(dao.observe(pkg), global.observe(), ticks()) { cfg, state, _ ->
        EffectivePolicy.resolve(cfg, pkg, state, clock.now())
    }.distinctUntilChanged()
    fun observeExcluded(): Flow<Set<String>> = combine(dao.observeAll(), global.observe(), ticks()) { apps, state, _ ->
        val now = clock.now()
        apps.filter { EffectivePolicy.resolve(it, it.pkg, state, now).level == ProtectLevel.OFF }.map { it.pkg }.toSet()
    }.distinctUntilChanged()
    suspend fun setLevel(pkg: String, level: ProtectLevel?) = setToggles(pkg) { it.copy(level = level) }
    suspend fun setRewarded(pkg: String, mode: RewardedMode) = setToggles(pkg) { it.copy(rewarded = mode) }
    suspend fun setToggles(pkg: String, transform: (AppConfigEntity) -> AppConfigEntity) = db.withTransaction {
        val updated = transform(requireNotNull(dao.get(pkg)) { "App 未登记: $pkg" })
        require(updated.pkg == pkg) { "不能改变 App 包名" }
        dao.upsert(updated)
    }
    suspend fun tempAllow(pkg: String) = db.withTransaction {
        val cfg = requireNotNull(dao.get(pkg)) { "App 未登记: $pkg" }
        // C2 checks the policy before allowing; afterward this app is OFF and cannot emit.
        signals.emit(pkg, SignalKind.TEMP_ALLOW)
        dao.upsert(cfg.copy(tempAllowUntil = Math.addExact(clock.now(), DataContract.TEMP_ALLOW_MS)))
    }
    suspend fun extendObservation(pkg: String, ms: Long) {
        require(ms >= 0); setToggles(pkg) { it.copy(observationEndsAt = Math.addExact(clock.now(), ms)) }
    }
    suspend fun endObservation(pkg: String) = setToggles(pkg) { it.copy(observationEndsAt = 0) }
    suspend fun observationDue(): List<AppConfigEntity> = dao.all().filter {
        it.level == null && it.observationEndsAt > 0 && it.observationEndsAt <= clock.now()
    }
}
