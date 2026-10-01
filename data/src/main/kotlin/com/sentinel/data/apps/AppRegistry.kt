package com.sentinel.data.apps

import androidx.room.withTransaction
import com.sentinel.data.Clock
import com.sentinel.data.contract.DataContract
import com.sentinel.data.db.*
import com.sentinel.rules.catalog.SensitiveApps

data class InstalledApp(val pkg: String, val label: String)

class AppRegistry(private val db: SentinelDatabase, private val clock: Clock) {
    suspend fun syncInstalled(apps: List<InstalledApp>, initial: Boolean) = db.withTransaction {
        val installed = apps.map { it.pkg }.toSet()
        db.appConfigDao().all().filter { it.pkg !in installed }.forEach { onPackageRemoved(it.pkg) }
        apps.forEach { register(it, initial) }
    }
    private suspend fun register(app: InstalledApp, initial: Boolean) {
        require(app.pkg.isNotBlank())
        if (db.appConfigDao().get(app.pkg) != null) return
        val now = clock.now()
        db.appConfigDao().upsert(AppConfigEntity(app.pkg, app.label, null,
            sensitive = SensitiveApps.isSensitive(app.pkg, app.label), firstSeenAt = now,
            observationEndsAt = if (initial) 0 else Math.addExact(now, DataContract.OBSERVATION_MS)))
    }
    suspend fun onPackageAdded(app: InstalledApp) = db.withTransaction { register(app, false) }
    suspend fun onPackageRemoved(pkg: String) = db.withTransaction {
        db.appConfigDao().deletePkg(pkg); db.overrideDao().deletePkg(pkg)
        db.jumpExceptionDao().deletePkg(pkg); db.rewardWindowDao().deletePkg(pkg)
    }
}
