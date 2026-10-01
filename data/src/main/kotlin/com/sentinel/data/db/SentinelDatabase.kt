package com.sentinel.data.db

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AppConfigEntity::class, GlobalStateEntity::class, SubscriptionEntity::class, UserRuleEntity::class, EventEntity::class, SignalEntity::class, RuleOverrideEntity::class, OpLogEntity::class, JumpExceptionEntity::class, RewardWindowEntity::class, EngineStatusEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class SentinelDatabase : RoomDatabase() {
    abstract fun appConfigDao(): AppConfigDao
    abstract fun globalStateDao(): GlobalStateDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun userRuleDao(): UserRuleDao
    abstract fun eventDao(): EventDao
    abstract fun signalDao(): SignalDao
    abstract fun overrideDao(): OverrideDao
    abstract fun opLogDao(): OpLogDao
    abstract fun jumpExceptionDao(): JumpExceptionDao
    abstract fun rewardWindowDao(): RewardWindowDao
    abstract fun engineStatusDao(): EngineStatusDao

    companion object {
        fun build(context: Context): SentinelDatabase = Room.databaseBuilder(
            context.applicationContext, SentinelDatabase::class.java, "sentinel.db"
        ).enableMultiInstanceInvalidation().addCallback(object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT INTO global_state (id, enabled, pausedUntil, ruleVersion) VALUES (0, 1, NULL, 0)")
            }
        }).build()
    }
}
