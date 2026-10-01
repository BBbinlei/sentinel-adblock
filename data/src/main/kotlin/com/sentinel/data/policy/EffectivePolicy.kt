package com.sentinel.data.policy

import com.sentinel.data.db.*

object EffectivePolicy {
    fun resolve(cfg: AppConfigEntity?, pkg: String, global: GlobalStateEntity, now: Long): EffectiveConfig {
        val level = when {
            !global.enabled || (global.pausedUntil ?: 0) > now -> ProtectLevel.OFF
            (cfg?.tempAllowUntil ?: 0) > now -> ProtectLevel.OFF
            cfg?.level != null -> cfg.level
            cfg?.sensitive == true -> ProtectLevel.OFF
            else -> ProtectLevel.STANDARD
        }
        return EffectiveConfig(pkg, level, cfg != null && cfg.level == null && cfg.observationEndsAt > 0,
            cfg?.splash ?: true, cfg?.rewarded ?: RewardedMode.SILENT, cfg?.shake ?: true,
            cfg?.jumpBack ?: true, cfg?.notify ?: true, cfg?.limitOverlay ?: false, cfg?.denyClipboard ?: false)
    }
}
