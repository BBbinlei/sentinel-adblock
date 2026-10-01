package com.sentinel.data.repo

import com.sentinel.data.Clock
import com.sentinel.data.db.*
import kotlinx.coroutines.flow.*
import androidx.room.withTransaction

class GlobalStateRepository(private val db: SentinelDatabase, private val clock: Clock) {
    private val dao = db.globalStateDao()
    fun observe(): Flow<GlobalStateEntity> = dao.observe().map { it ?: GlobalStateEntity() }
    suspend fun get(): GlobalStateEntity { dao.ensure(); return requireNotNull(dao.get()) }
    suspend fun setEnabled(on: Boolean) { dao.ensure(); dao.setEnabled(on) }
    suspend fun pauseFor(ms: Long = 300_000) { require(ms >= 0); dao.ensure(); dao.setPausedUntil(Math.addExact(clock.now(), ms)) }
    suspend fun resume() { dao.ensure(); dao.setPausedUntil(null) }
    suspend fun bumpRuleVersion(): Long = db.withTransaction { dao.ensure(); dao.bumpVersion(); requireNotNull(dao.get()).ruleVersion }
}
