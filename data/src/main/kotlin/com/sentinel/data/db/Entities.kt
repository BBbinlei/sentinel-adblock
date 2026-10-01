package com.sentinel.data.db

import androidx.room.*
import com.sentinel.rules.model.*

enum class ProtectLevel { OFF, STANDARD, STRONG }
enum class RewardedMode { SILENT, BLOCK, ASK }
enum class SubscriptionFormat { HOSTS, ADGUARD, GKD, SENTINEL_JSON }
enum class SignalKind { USER_UNDO, TEMP_ALLOW, RETRY_STORM, CRASH_DIALOG, COLD_START_LOOP, DROPBOX_CRASH }
enum class OverrideState { DISABLED, PINNED }
enum class EngineId { VPN, A11Y, NOTIFY, SYSTEM }
enum class EngineState { RUNNING, STOPPED, DEGRADED, NOT_SETUP }
enum class RuleOrigin { LEARNED, MANUAL }

@Entity("app_config") data class AppConfigEntity(@PrimaryKey val pkg: String, val label: String,
    val level: ProtectLevel?,            // null = 用户未设置，走默认
    val splash: Boolean = true, val rewarded: RewardedMode = RewardedMode.SILENT,
    val shake: Boolean = true, val jumpBack: Boolean = true, val notify: Boolean = true,
    val limitOverlay: Boolean = false, val denyClipboard: Boolean = false,
    val sensitive: Boolean, val tempAllowUntil: Long? = null,
    val firstSeenAt: Long, val observationEndsAt: Long)
@Entity("global_state") data class GlobalStateEntity(@PrimaryKey val id: Int = 0, val enabled: Boolean = true,
    val pausedUntil: Long? = null, val ruleVersion: Long = 0)
@Entity("subscriptions") data class SubscriptionEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val url: String, val format: SubscriptionFormat, val level: RuleLevel, val tag: DomainTag,
    val enabled: Boolean = true, val lastUpdatedAt: Long? = null, val ruleCount: Int = 0,
    val etag: String? = null, val lastError: String? = null)
@Entity("user_rules") data class UserRuleEntity(@PrimaryKey val id: String, val json: String, val origin: RuleOrigin, val createdAt: Long)
@Entity("events", indices = [Index("ts"), Index("pkg")]) data class EventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long, val pkg: String?, val kind: EventKind, val ruleId: String?, val detail: String? = null)
@Entity("signals") data class SignalEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val ts: Long,
    val pkg: String, val kind: SignalKind, val ruleId: String?, val detail: String? = null, val handled: Boolean = false)
@Entity("rule_overrides", primaryKeys = ["pkg", "ruleId"]) data class RuleOverrideEntity(val pkg: String,
    val ruleId: String, val state: OverrideState, val reason: String, val createdAt: Long)
@Entity("op_log") data class OpLogEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val ts: Long,
    val opId: String, val target: String, val beforeState: String, val command: String,
    val success: Boolean, val output: String, val undone: Boolean = false)
@Entity("jump_exceptions", primaryKeys = ["sourcePkg", "targetPkg"]) data class JumpExceptionEntity(val sourcePkg: String, val targetPkg: String)
@Entity("reward_windows") data class RewardWindowEntity(@PrimaryKey val pkg: String, val until: Long)
@Entity("engine_status") data class EngineStatusEntity(@PrimaryKey val engine: EngineId, val state: EngineState,
    val message: String?, val updatedAt: Long)


data class EffectiveConfig(val pkg: String, val level: ProtectLevel, val observing: Boolean,
    val splash: Boolean, val rewarded: RewardedMode, val shake: Boolean, val jumpBack: Boolean,
    val notify: Boolean, val limitOverlay: Boolean, val denyClipboard: Boolean)
