package com.sentinel.data.repo

import com.sentinel.data.Clock
import com.sentinel.data.db.*
import kotlinx.coroutines.flow.*

class OpLogRepository(private val dao: OpLogDao, private val clock: Clock) {
    suspend fun append(e: OpLogEntity): Long = dao.insert(e)
    fun observeAll(): Flow<List<OpLogEntity>> = dao.observeAll().map { it.sortedByDescending { e -> e.ts } }
    suspend fun latestApplied(opId: String): OpLogEntity? = dao.all().filter { it.opId == opId && it.success && !it.undone }.maxWithOrNull(compareBy<OpLogEntity> { it.ts }.thenBy { it.id })
    suspend fun markUndone(id: Long) = dao.markUndone(id)
}
