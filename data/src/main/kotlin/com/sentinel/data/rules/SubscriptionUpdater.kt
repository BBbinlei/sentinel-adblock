package com.sentinel.data.rules

import com.sentinel.data.Clock
import com.sentinel.data.db.*
import com.sentinel.data.repo.UserRuleRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*

data class UpdateReport(val updated: Int, val failed: Int, val ruleVersion: Long?)

class SubscriptionUpdater(private val db: SentinelDatabase, private val clock: Clock,
    private val client: OkHttpClient, private val cacheDir: File, private val store: RuleStore,
    private val users: UserRuleRepository) {
    companion object {
        // ponytail: one update at a time in the main process; per-store locks if independent profiles are introduced.
        private val updates = Mutex()
    }
    suspend fun updateAll(force: Boolean = false): UpdateReport = withContext(Dispatchers.IO) {
        updates.withLock {
            check(cacheDir.isDirectory || cacheDir.mkdirs()) { "无法创建订阅缓存目录" }
            var updated = 0
            var failed = 0
            for (sub in db.subscriptionDao().all().filter { it.enabled }) {
                val cache = File(cacheDir, "${sub.id}.txt")
                val tmp = File(cacheDir, "${sub.id}.tmp")
                try {
                    val request = Request.Builder().url(sub.url).apply {
                        if (!force && cache.isFile) sub.etag?.let { header("If-None-Match", it) }
                    }.build()
                    client.newCall(request).execute().use { response ->
                        val text = when {
                            response.code == 304 -> cache.readText()
                            response.isSuccessful -> requireNotNull(response.body).string()
                            else -> error("HTTP ${response.code}")
                        }
                        val parsed = RuleBuilder.parse(sub, text)
                        if (response.code != 304) {
                            FileOutputStream(tmp).use { it.write(text.toByteArray()); it.fd.sync() }
                            Files.move(tmp.toPath(), cache.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                            updated++
                        }
                        db.subscriptionDao().upsert(sub.copy(lastUpdatedAt = clock.now(), ruleCount = parsed.rules.size,
                            etag = if (response.code == 304) sub.etag else response.header("ETag"), lastError = null))
                    }
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    failed++
                    db.subscriptionDao().upsert(sub.copy(lastError = e.message ?: e.javaClass.simpleName))
                } finally { tmp.delete() }
            }
            UpdateReport(updated, failed, rebuild())
        }
    }
    suspend fun rebuildFromCache(): Long = withContext(Dispatchers.IO) { updates.withLock { rebuild() } }
    private suspend fun rebuild(): Long {
        val subs = db.subscriptionDao().all().filter { it.enabled }.mapNotNull { sub ->
            File(cacheDir, "${sub.id}.txt").takeIf { it.isFile }?.let { sub to it.readText() }
        }
        val built = RuleBuilder.build(subs, users.observeAll().first())
        return store.install(built)
    }
}
