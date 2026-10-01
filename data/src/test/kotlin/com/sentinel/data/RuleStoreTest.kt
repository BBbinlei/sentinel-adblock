package com.sentinel.data

import androidx.room.Room
import com.sentinel.data.db.*
import com.sentinel.data.repo.GlobalStateRepository
import com.sentinel.data.rules.*
import com.sentinel.rules.domain.DomainCompiler
import com.sentinel.rules.model.*
import com.sentinel.rules.parse.JsonRuleParser
import com.sentinel.rules.ui.BuiltInUiRules
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RuleStoreTest {
    private lateinit var db: SentinelDatabase
    private lateinit var dir: File
    private lateinit var global: GlobalStateRepository
    private lateinit var store: RuleStore
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SentinelDatabase::class.java).build()
        global = GlobalStateRepository(db, Clock { 1000 })
        dir = Files.createTempDirectory("sentinel-rules").toFile(); store = RuleStore(dir, global)
    }
    @After fun close() { db.close(); dir.deleteRecursively() }
    private fun built(domain: String) = BuiltRules(DomainCompiler.compile(listOf(DnsRule("dns:$domain", domain, DomainTag.AD, RuleLevel.STRONG, "test"))),
        JsonRuleParser.encode(BuiltInUiRules.all), JsonRuleParser.encode(listOf(NotifyRule(domain, "pkg", null, listOf("广告"), "test"))), emptyMap())
    @Test fun `UT-DA-4-01 installed matcher loaded`() = runBlocking<Unit> {
        store.install(built("ads.example.com")); assertNotNull(store.loadDomainMatcher()!!.lookup("ads.example.com"))
        assertEquals("ads.example.com", store.loadNotifyRules().single().id)
    }
    @Test fun `UT-DA-4-02 corrupt current falls back to previous`() = runBlocking<Unit> {
        store.install(built("old.example.com")); store.install(built("new.example.com"))
        File(dir, "domains.bin").writeText("corrupt"); File(dir, "ui.json").writeText("broken"); File(dir, "notify.json").writeText("broken")
        assertNotNull(store.loadDomainMatcher()!!.lookup("old.example.com"))
        assertNull(store.loadDomainMatcher()!!.lookup("new.example.com"))
        assertEquals("old.example.com", store.loadNotifyRules().single().id)
        assertEquals(BuiltInUiRules.all.size, store.loadUiIndex().lookup("pkg", null).size)
    }
    @Test fun `UT-DA-4-03 validation failure keeps all old files and version`() = runBlocking<Unit> {
        store.install(built("old.example.com")); val version = global.get().ruleVersion
        val old = File(dir, "domains.bin").readBytes()
        for (bad in listOf(built("new.example.com").copy(domainBytes = byteArrayOf(0)),
            built("new.example.com").copy(uiJson = "broken"), built("new.example.com").copy(notifyJson = "{}"))) {
            assertFailsWith<RuleInstallException> { store.install(bad) }
            assertContentEquals(old, File(dir, "domains.bin").readBytes())
            assertEquals(version, global.get().ruleVersion)
        }
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
    }
    @Test fun `UT-DA-4-04 missing and irrecoverable files use safe defaults`() {
        assertNull(store.loadDomainMatcher()); assertTrue(store.loadNotifyRules().isEmpty())
        assertEquals(BuiltInUiRules.all.size, store.loadUiIndex().lookup("pkg", null).size)
        File(dir, "domains.bin").writeText("bad"); File(dir, "domains.prev.bin").writeText("bad")
        assertNull(store.loadDomainMatcher())
    }
    @Test fun `UT-DA-4-05 install bumps version exactly once`() = runBlocking<Unit> {
        assertEquals(1L, store.install(built("old.example.com")))
        assertEquals(2L, store.install(built("new.example.com")))
        assertEquals(2L, global.get().ruleVersion)
    }
    @Test fun `version write failure restores previous generation`() = runBlocking<Unit> {
        store.install(built("old.example.com")); store.install(built("new.example.com"))
        db.close()
        assertFailsWith<RuleInstallException> { store.install(built("failed.example.com")) }
        assertNotNull(store.loadDomainMatcher()!!.lookup("new.example.com"))
        File(dir, "domains.bin").writeText("broken")
        assertNotNull(store.loadDomainMatcher()!!.lookup("old.example.com"))
    }
}
