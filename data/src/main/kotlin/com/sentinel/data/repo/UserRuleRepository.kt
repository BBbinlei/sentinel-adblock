package com.sentinel.data.repo

import com.sentinel.data.Clock
import com.sentinel.data.db.*
import kotlinx.coroutines.flow.*
import com.sentinel.rules.model.Rule
import com.sentinel.rules.parse.JsonRuleParser

class UserRuleRepository(private val dao: UserRuleDao, private val clock: Clock) {
    suspend fun add(rule: Rule, origin: RuleOrigin) {
        val json = JsonRuleParser.encode(listOf(rule))
        val validated = JsonRuleParser.parse(json).single()
        dao.upsert(UserRuleEntity(validated.id, JsonRuleParser.encode(listOf(validated)), origin, clock.now()))
    }
    fun observeAll(): Flow<List<Rule>> = dao.observeAll().map { rows -> rows.flatMap { JsonRuleParser.parse(it.json) } }
    suspend fun delete(id: String) = dao.delete(id)
}
