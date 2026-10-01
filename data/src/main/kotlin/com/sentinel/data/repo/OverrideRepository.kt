package com.sentinel.data.repo

import com.sentinel.data.Clock
import com.sentinel.data.db.*
import kotlinx.coroutines.flow.*
import androidx.room.withTransaction

class OverrideRepository(private val db: SentinelDatabase, private val clock: Clock) {
    private val dao = db.overrideDao()
    fun observeDisabled(): Flow<Map<String, Set<String>>> = dao.observeAll().map { rows ->
        rows.filter { it.state == OverrideState.DISABLED }.groupBy { it.pkg }.mapValues { (_, values) -> values.map { it.ruleId }.toSet() }
    }
    fun observeRecent(since: Long): Flow<List<RuleOverrideEntity>> = dao.observeAll().map { rows ->
        rows.filter { it.state == OverrideState.DISABLED && it.createdAt >= since }.sortedByDescending { it.createdAt }
    }
    suspend fun disable(pkg: String, ruleId: String, reason: String): Boolean = db.withTransaction {
        if (dao.get(pkg, ruleId)?.state == OverrideState.PINNED) false
        else { dao.upsert(RuleOverrideEntity(pkg, ruleId, OverrideState.DISABLED, reason, clock.now())); true }
    }
    suspend fun pin(pkg: String, ruleId: String) = dao.upsert(RuleOverrideEntity(pkg, ruleId, OverrideState.PINNED, "用户钉住", clock.now()))
    suspend fun remove(pkg: String, ruleId: String) = dao.remove(pkg, ruleId)
}
