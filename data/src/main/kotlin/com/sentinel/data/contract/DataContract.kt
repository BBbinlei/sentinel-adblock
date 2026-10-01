package com.sentinel.data.contract

object DataContract {
    const val OBSERVATION_MS = 3 * 86_400_000L
    const val TEMP_ALLOW_MS = 86_400_000L
    const val PAUSE_MS = 300_000L
    const val REFRESH_MS = 60_000L
    const val EVENT_RETENTION_MS = 30 * 86_400_000L
    const val RULE_UPDATE_WORK = "rule-update"
    const val RULE_UPDATE_HOURS = 24L
    const val DATABASE_NAME = "sentinel.db"
}
