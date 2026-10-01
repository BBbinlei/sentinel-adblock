package com.sentinel.data.repo

import com.sentinel.data.Clock
import com.sentinel.data.db.*
import kotlinx.coroutines.flow.*

class EngineStatusRepository(private val dao: EngineStatusDao, private val clock: Clock) {
    suspend fun report(engine: EngineId, state: EngineState, message: String? = null) {
        require(state != EngineState.DEGRADED || !message.isNullOrBlank())
        dao.upsert(EngineStatusEntity(engine, state, message, clock.now()))
    }
    fun observeAll(): Flow<Map<EngineId, EngineStatusEntity>> = dao.observeAll().map { rows -> rows.associateBy { it.engine } }
}
