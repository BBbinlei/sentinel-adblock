package com.sentinel.data

import androidx.room.Room
import com.sentinel.data.db.*
import com.sentinel.data.repo.*
import com.sentinel.data.rules.*
import com.sentinel.rules.catalog.*
import com.sentinel.rules.domain.DomainMatcher
import com.sentinel.rules.model.*
import com.sentinel.rules.parse.JsonRuleParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubscriptionUpdaterTest {
    private lateinit var db: SentinelDatabase
    private lateinit var dir: File
    private lateinit var server: MockWebServer
    private lateinit var global: GlobalStateRepository
    private lateinit var store: RuleStore
    private lateinit var updater: SubscriptionUpdater
    private val clock = Clock { 1000L }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SentinelDatabase::class.java).build()
        dir = Files.createTempDirectory("sentinel-subs").toFile(); server = MockWebServer(); server.start()
        global = GlobalStateRepository(db, clock); store = RuleStore(File(dir, "rules"), global)
        updater = SubscriptionUpdater(db, clock, OkHttpClient(), File(dir, "subs"), store, UserRuleRepository(db.userRuleDao(), clock))
    }
    @After fun close() { db.close(); server.shutdown(); dir.deleteRecursively() }
    private fun sub(id: Long = 1, format: SubscriptionFormat = SubscriptionFormat.ADGUARD) = SubscriptionEntity(id, "test", server.url("/$id").toString(), format, RuleLevel.STRONG, DomainTag.AD)
    @Test fun `UT-DA-5-01 no subscriptions has standard domains and HttpDNS`() {
        val built = RuleBuilder.build(emptyList(), emptyList())
        val matcher = DomainMatcher(ByteBuffer.wrap(built.domainBytes))
        assertNotNull(matcher.lookup("gdt.qq.com"))
        StandardDomains.rules().forEach { assertNotNull(matcher.lookup(it.domain)) }
        HttpDnsCatalog.domains.filterNot { com.sentinel.rules.domain.NeverBlockList.contains(it) }.forEach {
            assertEquals(DomainTag.HTTPDNS, matcher.lookup(it)!!.tag)
        }
    }
    @Test fun `UT-DA-5-02 stats count sources duplicates and skips`() {
        val built = RuleBuilder.build(listOf(sub() to "||ads.example.com^\n||ads.example.com^\nunsupported"), emptyList())
        assertEquals(2, built.stats["subscription:1"])
        assertEquals(1, built.stats["subscription:1:skipped"])
        assertEquals(1, built.stats["skipped"])
        assertEquals(1, built.stats["duplicates"])
        val matcher = DomainMatcher(ByteBuffer.wrap(built.domainBytes))
        assertEquals(matcher.size, built.stats["domains"])
        val user = listOf<Rule>(UiRule("ui", RuleScope.Global, "[text=\"关闭\"]", UiAction.CLICK, UiPhase.ANYTIME, "user"), NotifyRule("notify", null, null, listOf("广告"), "user"))
        val all = RuleBuilder.build(listOf(sub(2, SubscriptionFormat.HOSTS) to "0.0.0.0 hosts.example.com",
            sub(3, SubscriptionFormat.GKD) to """{"apps":[{"id":"com.test.app","groups":[{"key":1,"rules":["[text=\"跳过\"]"]}]}]}""",
            sub(4, SubscriptionFormat.SENTINEL_JSON) to JsonRuleParser.encode(listOf(DnsRule("dns:json.example.com", "json.example.com", DomainTag.AD, RuleLevel.STRONG, "json")))), user)
        assertNotNull(DomainMatcher(ByteBuffer.wrap(all.domainBytes)).lookup("hosts.example.com"))
        assertNotNull(DomainMatcher(ByteBuffer.wrap(all.domainBytes)).lookup("json.example.com"))
        assertEquals(5, JsonRuleParser.parse(all.uiJson).size)
        assertEquals(user.filterIsInstance<NotifyRule>(), JsonRuleParser.parse(all.notifyJson))
    }
    @Test fun `UT-DA-5-03 failed download keeps cache and records error`() = runBlocking<Unit> {
        db.subscriptionDao().upsert(sub()); File(dir, "subs").mkdirs(); File(dir, "subs/1.txt").writeText("||cached.example.com^")
        server.enqueue(MockResponse().setResponseCode(500))
        val report = updater.updateAll()
        assertEquals(1, report.failed); assertEquals(0, report.updated)
        assertNotNull(db.subscriptionDao().all().single().lastError)
        assertNotNull(store.loadDomainMatcher()!!.lookup("cached.example.com"))
    }
    @Test fun `UT-DA-5-04 ETag and 304 reuse cache`() = runBlocking<Unit> {
        db.subscriptionDao().upsert(sub())
        server.enqueue(MockResponse().setBody("||cached.example.com^").setHeader("ETag", "v1"))
        assertEquals(1, updater.updateAll().updated); server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(304))
        assertEquals(0, updater.updateAll().failed)
        assertEquals("v1", server.takeRequest().getHeader("If-None-Match"))
        assertEquals("||cached.example.com^", File(dir, "subs/1.txt").readText())
        assertNotNull(store.loadDomainMatcher()!!.lookup("cached.example.com"))
        server.enqueue(MockResponse().setBody("||forced.example.com^"))
        updater.updateAll(force = true); assertNull(server.takeRequest().getHeader("If-None-Match"))
    }
    @Test fun `UT-DA-5-05 build exception cannot install or bump`() = runBlocking<Unit> {
        updater.rebuildFromCache(); val old = global.get().ruleVersion
        db.userRuleDao().upsert(UserRuleEntity("broken", "invalid JSON", RuleOrigin.MANUAL, 0))
        assertFailsWith<Exception> { updater.rebuildFromCache() }
        assertFailsWith<Exception> { updater.updateAll() }
        assertEquals(old, global.get().ruleVersion)
        assertNotNull(store.loadDomainMatcher()!!.lookup("gdt.qq.com"))
    }
}
