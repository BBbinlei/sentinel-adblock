package com.sentinel.data

import android.content.Context
import androidx.room.Room
import com.sentinel.data.apps.*
import com.sentinel.data.contract.DataContract
import com.sentinel.data.db.*
import com.sentinel.data.di.dataModule
import com.sentinel.data.repo.*
import com.sentinel.data.rules.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataModuleIntegrationTest {
    @Test fun `MT-DA-01 fresh database seeds defaults and builds offline rules`() = runBlocking<Unit> {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DataContract.DATABASE_NAME)
        val ruleDir = File(context.filesDir, "rules"); val subDir = File(context.filesDir, "subs")
        ruleDir.deleteRecursively(); subDir.deleteRecursively()
        val app = koinApplication { androidContext(context); modules(dataModule) }
        val db = app.koin.get<SentinelDatabase>()
        try {
            assertEquals(GlobalStateEntity(), app.koin.get<GlobalStateRepository>().get())
            assertEquals(DefaultSubscriptions.all, db.subscriptionDao().all())
            val version = app.koin.get<SubscriptionUpdater>().rebuildFromCache()
            assertEquals(1L, version)
            assertEquals(version, app.koin.get<GlobalStateRepository>().get().ruleVersion)
            assertNotNull(app.koin.get<RuleStore>().loadDomainMatcher()!!.lookup("gdt.qq.com"))
        } finally { db.close(); app.close(); context.deleteDatabase(DataContract.DATABASE_NAME); ruleDir.deleteRecursively(); subDir.deleteRecursively() }
    }
    @Test fun `MT-DA-02 app lifecycle sensitivity observation allow expiry uninstall`() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SentinelDatabase::class.java).build()
        var now = 1000L
        val clock = Clock { now }
        val global = GlobalStateRepository(db, clock); val signals = SignalRepository(db, clock)
        val configs = AppConfigRepository(db, clock, global, signals); val registry = AppRegistry(db, clock)
        val bank = "com.x.bank"; val normal = "com.example.reader"
        try {
            registry.onPackageAdded(InstalledApp(bank, "某某银行")); assertEquals(setOf(bank), configs.observeExcluded().first())
            registry.onPackageAdded(InstalledApp(normal, "阅读器")); assertTrue(configs.effective(normal).observing)
            configs.tempAllow(normal)
            assertEquals(setOf(bank, normal), configs.observeExcluded().first())
            val signal = signals.observeUnhandled().first().single()
            assertEquals(normal, signal.pkg); assertEquals(SignalKind.TEMP_ALLOW, signal.kind); assertNull(signal.ruleId)
            now += DataContract.TEMP_ALLOW_MS
            assertEquals(setOf(bank), configs.observeExcluded().first()); assertTrue(configs.effective(normal).observing)
            val overrides = OverrideRepository(db, clock); overrides.disable(normal, "r", "test")
            val jumps = JumpExceptionRepository(db.jumpExceptionDao(), clock); jumps.add(normal, bank); jumps.add(bank, normal)
            RewardWindowRepository(db.rewardWindowDao(), clock).open(normal)
            registry.onPackageRemoved(normal)
            assertNull(configs.observe(normal).first()); assertTrue(overrides.observeDisabled().first().isEmpty())
            assertFalse(jumps.isExcepted(normal, bank)); assertFalse(jumps.isExcepted(bank, normal))
            assertTrue(db.rewardWindowDao().all().isEmpty()); assertEquals(setOf(bank), configs.observeExcluded().first())
        } finally { db.close() }
    }
    @Test fun `MT-DA-03 partial subscription failure installs success and cache together`() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SentinelDatabase::class.java).build()
        val dir = Files.createTempDirectory("sentinel-g2").toFile(); val server = MockWebServer()
        val clock = Clock { 1000L }; val global = GlobalStateRepository(db, clock)
        val store = RuleStore(File(dir, "rules"), global); val cache = File(dir, "subs").apply { mkdirs() }
        try {
            server.start()
            db.subscriptionDao().upsert(SubscriptionEntity(101, "success", server.url("/success").toString(), SubscriptionFormat.ADGUARD, com.sentinel.rules.model.RuleLevel.STRONG, com.sentinel.rules.model.DomainTag.AD))
            db.subscriptionDao().upsert(SubscriptionEntity(102, "failure", server.url("/failure").toString(), SubscriptionFormat.ADGUARD, com.sentinel.rules.model.RuleLevel.STRONG, com.sentinel.rules.model.DomainTag.AD))
            File(cache, "102.txt").writeText("||fallback.example.com^")
            val updater = SubscriptionUpdater(db, clock, OkHttpClient(), cache, store, UserRuleRepository(db.userRuleDao(), clock))
            updater.rebuildFromCache(); val oldVersion = global.get().ruleVersion
            server.enqueue(MockResponse().setBody("||downloaded.example.com^").setHeader("ETag", "new"))
            server.enqueue(MockResponse().setResponseCode(503))
            val report = updater.updateAll()
            assertEquals(1, report.updated); assertEquals(1, report.failed)
            assertEquals(oldVersion + 1, report.ruleVersion); assertEquals(report.ruleVersion, global.get().ruleVersion)
            val matcher = store.loadDomainMatcher()!!
            assertNotNull(matcher.lookup("downloaded.example.com")); assertNotNull(matcher.lookup("fallback.example.com")); assertNotNull(matcher.lookup("gdt.qq.com"))
            val rows = db.subscriptionDao().all().associateBy { it.id }
            assertNull(rows[101]!!.lastError); assertEquals("new", rows[101]!!.etag); assertEquals(1, rows[101]!!.ruleCount)
            assertNotNull(rows[102]!!.lastError); assertEquals("||fallback.example.com^", File(cache, "102.txt").readText())
        } finally { db.close(); server.shutdown(); dir.deleteRecursively() }
    }
}
