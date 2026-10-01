package com.sentinel.data.repo

import com.sentinel.data.Clock
import com.sentinel.data.db.*
import kotlinx.coroutines.flow.*
import com.sentinel.data.contract.RewardWindowContract

class RewardWindowRepository(private val dao: RewardWindowDao, private val clock: Clock) {
    suspend fun open(pkg: String, ms: Long = RewardWindowContract.TTL_MS) { require(pkg.isNotBlank() && ms >= 0); dao.upsert(RewardWindowEntity(pkg, Math.addExact(clock.now(), ms))) }
    fun observeOpen(): Flow<Map<String, Long>> = dao.observeAll().map { rows -> rows.associate { it.pkg to it.until } }
}
