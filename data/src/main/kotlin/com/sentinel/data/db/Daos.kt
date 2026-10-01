package com.sentinel.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface AppConfigDao {
    @Upsert suspend fun upsert(entity: AppConfigEntity)
    @Query("SELECT * FROM app_config") suspend fun all(): List<AppConfigEntity>
    @Query("SELECT * FROM app_config") fun observeAll(): Flow<List<AppConfigEntity>>
    @Query("SELECT * FROM app_config WHERE pkg = :pkg") suspend fun get(pkg: String): AppConfigEntity?
    @Query("SELECT * FROM app_config WHERE pkg = :pkg") fun observe(pkg: String): Flow<AppConfigEntity?>
    @Query("DELETE FROM app_config WHERE pkg = :pkg") suspend fun deletePkg(pkg: String)
}

@Dao interface GlobalStateDao {
    @Upsert suspend fun upsert(entity: GlobalStateEntity)
    @Query("SELECT * FROM global_state") suspend fun all(): List<GlobalStateEntity>
    @Query("SELECT * FROM global_state") fun observeAll(): Flow<List<GlobalStateEntity>>
    @Query("SELECT * FROM global_state WHERE id = 0") suspend fun get(): GlobalStateEntity?
    @Query("SELECT * FROM global_state WHERE id = 0") fun observe(): Flow<GlobalStateEntity?>
    @Query("UPDATE global_state SET enabled = :enabled WHERE id = 0") suspend fun setEnabled(enabled: Boolean)
    @Query("UPDATE global_state SET pausedUntil = :until WHERE id = 0") suspend fun setPausedUntil(until: Long?)
    @Query("UPDATE global_state SET ruleVersion = ruleVersion + 1 WHERE id = 0") suspend fun bumpVersion()
}

@Dao interface SubscriptionDao {
    @Upsert suspend fun upsert(entity: SubscriptionEntity)
    @Query("SELECT * FROM subscriptions") suspend fun all(): List<SubscriptionEntity>
    @Query("SELECT * FROM subscriptions") fun observeAll(): Flow<List<SubscriptionEntity>>
}

@Dao interface UserRuleDao {
    @Upsert suspend fun upsert(entity: UserRuleEntity)
    @Query("SELECT * FROM user_rules") suspend fun all(): List<UserRuleEntity>
    @Query("SELECT * FROM user_rules") fun observeAll(): Flow<List<UserRuleEntity>>
    @Query("DELETE FROM user_rules WHERE id = :id") suspend fun delete(id: String)
}

@Dao interface EventDao {
    @Insert suspend fun insert(entity: EventEntity): Long
    @Query("SELECT * FROM events") suspend fun all(): List<EventEntity>
    @Query("SELECT * FROM events") fun observeAll(): Flow<List<EventEntity>>
    @Query("DELETE FROM events WHERE ts < :before") suspend fun prune(before: Long)
}

@Dao interface SignalDao {
    @Insert suspend fun insert(entity: SignalEntity): Long
    @Query("SELECT * FROM signals") suspend fun all(): List<SignalEntity>
    @Query("SELECT * FROM signals") fun observeAll(): Flow<List<SignalEntity>>
    @Query("UPDATE signals SET handled = 1 WHERE id IN (:ids)") suspend fun markHandled(ids: List<Long>)
}

@Dao interface OverrideDao {
    @Upsert suspend fun upsert(entity: RuleOverrideEntity)
    @Query("SELECT * FROM rule_overrides") suspend fun all(): List<RuleOverrideEntity>
    @Query("SELECT * FROM rule_overrides") fun observeAll(): Flow<List<RuleOverrideEntity>>
    @Query("SELECT * FROM rule_overrides WHERE pkg = :pkg AND ruleId = :ruleId") suspend fun get(pkg: String, ruleId: String): RuleOverrideEntity?
    @Query("DELETE FROM rule_overrides WHERE pkg = :pkg AND ruleId = :ruleId") suspend fun remove(pkg: String, ruleId: String)
    @Query("DELETE FROM rule_overrides WHERE pkg = :pkg") suspend fun deletePkg(pkg: String)
}

@Dao interface OpLogDao {
    @Insert suspend fun insert(entity: OpLogEntity): Long
    @Query("SELECT * FROM op_log") suspend fun all(): List<OpLogEntity>
    @Query("SELECT * FROM op_log") fun observeAll(): Flow<List<OpLogEntity>>
    @Query("UPDATE op_log SET undone = 1 WHERE id = :id") suspend fun markUndone(id: Long)
}

@Dao interface JumpExceptionDao {
    @Upsert suspend fun upsert(entity: JumpExceptionEntity)
    @Query("SELECT * FROM jump_exceptions") suspend fun all(): List<JumpExceptionEntity>
    @Query("SELECT * FROM jump_exceptions") fun observeAll(): Flow<List<JumpExceptionEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM jump_exceptions WHERE sourcePkg = :src AND targetPkg = :tgt)") suspend fun isExcepted(src: String, tgt: String): Boolean
    @Query("DELETE FROM jump_exceptions WHERE sourcePkg = :pkg OR targetPkg = :pkg") suspend fun deletePkg(pkg: String)
}

@Dao interface RewardWindowDao {
    @Upsert suspend fun upsert(entity: RewardWindowEntity)
    @Query("SELECT * FROM reward_windows") suspend fun all(): List<RewardWindowEntity>
    @Query("SELECT * FROM reward_windows") fun observeAll(): Flow<List<RewardWindowEntity>>
    @Query("DELETE FROM reward_windows WHERE pkg = :pkg") suspend fun deletePkg(pkg: String)
}

@Dao interface EngineStatusDao {
    @Upsert suspend fun upsert(entity: EngineStatusEntity)
    @Query("SELECT * FROM engine_status") suspend fun all(): List<EngineStatusEntity>
    @Query("SELECT * FROM engine_status") fun observeAll(): Flow<List<EngineStatusEntity>>
}

