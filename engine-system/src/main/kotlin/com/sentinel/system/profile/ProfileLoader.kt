package com.sentinel.system.profile

import kotlinx.serialization.json.Json

object ProfileLoader {
    fun parse(json: String): ColorOsProfile = Json.decodeFromString<ColorOsProfile>(json).also { profile ->
        require(profile.romPrefix.isNotBlank())
        require(profile.ops.all { it.id.isNotBlank() })
        require(profile.ops.map { it.id }.distinct().size == profile.ops.size)
        profile.ops.forEach { op ->
            if (op.kind == OpKind.WIZARD) require(op.intent != null)
            else {
                require(!op.apply.isNullOrBlank() && !op.revert.isNullOrBlank() && !op.probe.isNullOrBlank())
                Regex(requireNotNull(op.appliedRegex))
            }
            require(!(op.verified && op.kind == OpKind.UNINSTALL && !op.optional))
        }
    }

    fun select(romVersion: String, profiles: List<ColorOsProfile>): ColorOsProfile? =
        profiles.filter { romVersion.startsWith(it.romPrefix) }.maxByOrNull { it.romPrefix.length }
}
