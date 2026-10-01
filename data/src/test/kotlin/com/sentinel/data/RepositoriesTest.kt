package com.sentinel.data

import androidx.room.Room
import com.sentinel.data.contract.*
import com.sentinel.data.db.*
import com.sentinel.data.repo.*
import com.sentinel.rules.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoriesTest {
    private lateinit var db: SentinelDatabase
    private var now = 1_800_000_000_000L
    private val clock = Clock { now }
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SentinelDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun `UT-DA-2-01 pause and atomic version`() = runBlocking {
        val r = GlobalStateRepository(db, clock)
        r.pauseFor(); assertEquals(now + 300_000, r.get().pausedUntil)
        r.resume(); assertNull(r.get().pausedUntil)
        assertEquals(1, r.bumpRuleVersion()); assertEquals(2, r.bumpRuleVersion())
    }
    @Test fun `UT-DA-2-02 local midnight count`() = runBlocking {
        val midnight = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        db.eventDao().insert(EventEntity(ts = midnight - 1, pkg = "p", kind = EventKind.DNS_BLOCKED, ruleId = null))
        db.eventDao().insert(EventEntity(ts = midnight, pkg = "p", kind = EventKind.DNS_BLOCKED, ruleId = null))
        assertEquals(1, EventRepository(db.eventDao(), clock).observeTodayCount().first())
    }
    @Test fun `UT-DA-2-03 prune thirty days`() = runBlocking {
        val cutoff = now - 30 * 86_400_000L
        db.eventDao().insert(EventEntity(ts = cutoff - 1, pkg = null, kind = EventKind.DNS_BLOCKED, ruleId = null))
        db.eventDao().insert(EventEntity(ts = cutoff, pkg = null, kind = EventKind.DNS_BLOCKED, ruleId = null))
        EventRepository(db.eventDao(), clock).prune(); assertEquals(listOf(cutoff), db.eventDao().all().map { it.ts })
    }
    @Test fun `UT-DA-2-04 pinned rules cannot disable`() = runBlocking {
        val r = OverrideRepository(db, clock); r.pin("p", "r")
        assertFalse(r.disable("p", "r", "test")); assertEquals(OverrideState.PINNED, db.overrideDao().all().single().state)
    }
    @Test fun `UT-DA-2-05 disabled grouped by package`() = runBlocking {
        val r = OverrideRepository(db, clock)
        r.disable("p", "a", "reason"); r.disable("p", "b", "reason"); r.disable("q", "c", "reason"); r.pin("q", "d")
        assertEquals(mapOf("p" to setOf("a", "b"), "q" to setOf("c")), r.observeDisabled().first())
    }
    @Test fun `UT-DA-2-06 unique recent rule hits`() = runBlocking {
        val r = EventRepository(db.eventDao(), clock)
        r.log("p", EventKind.DNS_BLOCKED, "r"); r.log("p", EventKind.DNS_BLOCKED, "r"); r.log("q", EventKind.DNS_BLOCKED, "s")
        assertEquals(listOf("r"), r.rulesHitSince("p", now))
    }
    @Test fun `UT-DA-2-07 reward deadline`() = runBlocking {
        val r = RewardWindowRepository(db.rewardWindowDao(), clock); r.open("p")
        assertEquals(mapOf("p" to now + RewardWindowContract.TTL_MS), r.observeOpen().first())
    }
    @Test fun `UT-DA-2-08 user rules round trip`() = runBlocking {
        val r = UserRuleRepository(db.userRuleDao(), clock)
        val rules = listOf<Rule>(DnsRule("dns:ads.example.com", "ads.example.com", DomainTag.AD, RuleLevel.STRONG, "user"),
            UiRule("ui", RuleScope.App("p"), "[text=\"跳过\"]", UiAction.CLICK, UiPhase.LAUNCH, "user"),
            NotifyRule("n", "p", null, listOf("促销"), "user"))
        rules.forEach { r.add(it, RuleOrigin.MANUAL) }; assertEquals(rules.toSet(), r.observeAll().first().toSet())
        r.delete("ui"); assertEquals(2, r.observeAll().first().size)
    }
    @Test fun `UT-DA-2-09 recent disabled only`() = runBlocking {
        val r = OverrideRepository(db, clock); r.disable("p", "old", "old")
        now++; r.disable("p", "new", "new"); r.pin("p", "pin")
        assertEquals(listOf("new"), r.observeRecent(now).first().map { it.ruleId })
    }
    @Test fun `UT-DA-2-10 reward contract defaults`() = runBlocking {
        assertEquals(60_000L, RewardWindowContract.TTL_MS)
        assertEquals(setOf(DomainTag.AD_SDK), RewardWindowContract.RELEASED_TAGS)
        RewardWindowRepository(db.rewardWindowDao(), clock).open("p")
        assertEquals(now + RewardWindowContract.TTL_MS, db.rewardWindowDao().all().single().until)
    }
    @Test fun `UT-DA-2-11 every signal declares its emitter`() {
        assertEquals(SignalKind.entries.toSet(), SignalContract.EMITTERS.keys)
        assertNull(SignalContract.EMITTERS[SignalKind.TEMP_ALLOW])
        assertEquals(EngineId.VPN, SignalContract.EMITTERS[SignalKind.RETRY_STORM])
        assertEquals(EngineId.SYSTEM, SignalContract.EMITTERS[SignalKind.DROPBOX_CRASH])
        listOf(SignalKind.USER_UNDO, SignalKind.CRASH_DIALOG, SignalKind.COLD_START_LOOP).forEach { assertEquals(EngineId.A11Y, SignalContract.EMITTERS[it]) }
    }
    @Test fun `signals validate package and effective level and tolerate write failure`() = runBlocking {
        val r = SignalRepository(db, clock)
        r.emit("", SignalKind.USER_UNDO)
        db.appConfigDao().upsert(AppConfigEntity("com.test.off", "off", ProtectLevel.OFF, sensitive = false, firstSeenAt = 0, observationEndsAt = 0))
        r.emit("com.test.off", SignalKind.USER_UNDO)
        r.emit("com.test.active", SignalKind.USER_UNDO)
        assertEquals(listOf("com.test.active"), r.observeUnhandled().first().map { it.pkg })
        r.markHandled(db.signalDao().all().map { it.id }); assertTrue(r.observeUnhandled().first().isEmpty())
        assertEquals(1, r.countSince("com.test.active", setOf(SignalKind.USER_UNDO), now))
        db.close(); r.emit("com.test.active", SignalKind.CRASH_DIALOG)
    }
    @Test fun `UT-DA-2-12 repeated reward replaces deadline`() = runBlocking {
        val r = RewardWindowRepository(db.rewardWindowDao(), clock); r.open("p")
        now += 5000; r.open("p"); assertEquals(now + RewardWindowContract.TTL_MS, r.observeOpen().first()["p"])
        assertEquals(1, db.rewardWindowDao().all().size)
    }
}
