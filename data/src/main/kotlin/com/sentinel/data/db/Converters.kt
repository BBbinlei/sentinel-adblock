package com.sentinel.data.db

import androidx.room.TypeConverter
import com.sentinel.rules.model.*

class Converters {
    @TypeConverter fun fromProtectLevel(value: ProtectLevel): String = value.name
    @TypeConverter fun toProtectLevel(value: String): ProtectLevel = ProtectLevel.valueOf(value)
    @TypeConverter fun fromRewardedMode(value: RewardedMode): String = value.name
    @TypeConverter fun toRewardedMode(value: String): RewardedMode = RewardedMode.valueOf(value)
    @TypeConverter fun fromSubscriptionFormat(value: SubscriptionFormat): String = value.name
    @TypeConverter fun toSubscriptionFormat(value: String): SubscriptionFormat = SubscriptionFormat.valueOf(value)
    @TypeConverter fun fromSignalKind(value: SignalKind): String = value.name
    @TypeConverter fun toSignalKind(value: String): SignalKind = SignalKind.valueOf(value)
    @TypeConverter fun fromOverrideState(value: OverrideState): String = value.name
    @TypeConverter fun toOverrideState(value: String): OverrideState = OverrideState.valueOf(value)
    @TypeConverter fun fromEngineId(value: EngineId): String = value.name
    @TypeConverter fun toEngineId(value: String): EngineId = EngineId.valueOf(value)
    @TypeConverter fun fromEngineState(value: EngineState): String = value.name
    @TypeConverter fun toEngineState(value: String): EngineState = EngineState.valueOf(value)
    @TypeConverter fun fromRuleOrigin(value: RuleOrigin): String = value.name
    @TypeConverter fun toRuleOrigin(value: String): RuleOrigin = RuleOrigin.valueOf(value)
    @TypeConverter fun fromRuleLevel(value: RuleLevel): String = value.name
    @TypeConverter fun toRuleLevel(value: String): RuleLevel = RuleLevel.valueOf(value)
    @TypeConverter fun fromDomainTag(value: DomainTag): String = value.name
    @TypeConverter fun toDomainTag(value: String): DomainTag = DomainTag.valueOf(value)
    @TypeConverter fun fromEventKind(value: EventKind): String = value.name
    @TypeConverter fun toEventKind(value: String): EventKind = EventKind.valueOf(value)
}
