package com.sentinel.data.repo

import com.sentinel.data.Clock
import com.sentinel.data.db.*
import kotlinx.coroutines.flow.*
import com.sentinel.rules.model.EventKind
import java.time.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)

class EventRepository(private val dao: EventDao, private val clock: Clock) {
    suspend fun log(pkg: String?, kind: EventKind, ruleId: String?, detail: String? = null) {
        dao.insert(EventEntity(ts = clock.now(), pkg = pkg, kind = kind, ruleId = ruleId, detail = detail))
    }
    fun observeTodayCount(): Flow<Int> = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            val day = Instant.ofEpochMilli(clock.now()).atZone(zone).toLocalDate()
            emit(day.atStartOfDay(zone).toInstant().toEpochMilli())
            delay(60_000)
        }
    }.distinctUntilChanged().flatMapLatest { dao.observeCount(it) }
    fun observeCountByKind(since: Long): Flow<Map<EventKind, Int>> = dao.countByKind(since).map { rows -> rows.associate { it.kind to it.count } }
    fun observeCountByPkg(since: Long): Flow<Map<String, Int>> = dao.countByPkg(since).map { rows -> rows.associate { it.pkg to it.count } }
    fun observeRecent(kinds: Set<EventKind>, limit: Int): Flow<List<EventEntity>> { require(limit >= 0); return dao.recent(kinds, limit) }
    suspend fun rulesHitSince(pkg: String, since: Long): List<String> = dao.rulesHitSince(pkg, since)
    suspend fun prune() = dao.prune(clock.now() - 30 * 86_400_000L)
}
