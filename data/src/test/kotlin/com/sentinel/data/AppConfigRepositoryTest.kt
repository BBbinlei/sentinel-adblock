package com.sentinel.data

import androidx.room.Room
import app.cash.turbine.test
import com.sentinel.data.apps.*
import com.sentinel.data.db.*
import com.sentinel.data.repo.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppConfigRepositoryTest {
    private lateinit var db: SentinelDatabase
    private var now = 1000L
    private val clock = Clock { now }
    private val global get() = GlobalStateRepository(db, clock)
    private val signals get() = SignalRepository(db, clock)
    private val repo get() = AppConfigRepository(db, clock, global, signals)
    private val registry get() = AppRegistry(db, clock)
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SentinelDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun `UT-DA-3-01 new bank immediately excluded`() = runBlocking {
        registry.onPackageAdded(InstalledApp("com.x.bank", "某某银行"))
        assertTrue("com.x.bank" in repo.observeExcluded().first())
    }
    @Test fun `UT-DA-3-02 ordinary app observed`() = runBlocking {
        registry.onPackageAdded(InstalledApp("com.x.normal", "Normal"))
        assertEquals(now + 259_200_000, repo.observe("com.x.normal").first()!!.observationEndsAt)
        assertTrue(repo.effective("com.x.normal").observing)
    }
    @Test fun `UT-DA-3-03 initial apps not observed and later sync preserves config`() = runBlocking {
        registry.syncInstalled(listOf(InstalledApp("com.x.normal", "Normal")), initial = true)
        assertEquals(0, repo.observeAll().first().single().observationEndsAt)
        repo.setLevel("com.x.normal", ProtectLevel.STRONG)
        val saved = repo.observeAll().first().single()
        registry.syncInstalled(listOf(InstalledApp("com.x.normal", "Changed"), InstalledApp("com.x.new", "New")), initial = false)
        assertEquals(saved, repo.observe("com.x.normal").first())
        assertTrue(repo.effective("com.x.new").observing)
        registry.syncInstalled(listOf(InstalledApp("com.x.normal", "Normal")), initial = false)
        assertNull(repo.observe("com.x.new").first())
    }
    @Test fun `UT-DA-3-04 temp allow emits signal and expires`() = runBlocking {
        registry.onPackageAdded(InstalledApp("com.x.normal", "Normal"))
        repo.tempAllow("com.x.normal")
        assertTrue("com.x.normal" in repo.observeExcluded().first())
        assertEquals(SignalKind.TEMP_ALLOW, signals.observeUnhandled().first().single().kind)
        assertNull(signals.observeUnhandled().first().single().ruleId)
        now += 86_400_000
        assertFalse("com.x.normal" in repo.observeExcluded().first())
    }
    @Test fun `UT-DA-3-05 uninstall cleans all specified tables and both jump ends`() = runBlocking {
        registry.onPackageAdded(InstalledApp("com.x.normal", "Normal"))
        OverrideRepository(db, clock).disable("com.x.normal", "r", "reason")
        val jumps = JumpExceptionRepository(db.jumpExceptionDao(), clock)
        jumps.add("com.x.normal", "other"); jumps.add("other", "com.x.normal"); jumps.add("other", "keep")
        RewardWindowRepository(db.rewardWindowDao(), clock).open("com.x.normal")
        registry.onPackageRemoved("com.x.normal")
        assertTrue(db.appConfigDao().all().isEmpty()); assertTrue(db.overrideDao().all().isEmpty())
        assertTrue(db.rewardWindowDao().all().isEmpty()); assertEquals(listOf(JumpExceptionEntity("other", "keep")), db.jumpExceptionDao().all())
    }
    @Test fun `UT-DA-3-06 pause expires with sixty second recomputation`() = runTest {
        db.close()
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SentinelDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler)).allowMainThreadQueries().build()
        registry.syncInstalled(listOf(InstalledApp("com.x.normal", "Normal")), true)
        global.pauseFor(30_000)
        repo.observeExcluded().test {
            assertEquals(setOf("com.x.normal"), awaitItem())
            now += 60_000
            advanceTimeBy(60_000); runCurrent()
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
    @Test fun `UT-DA-3-07 due observation requires unset level and positive deadline`() = runBlocking {
        listOf("due", "future", "explicit", "ended").forEach { registry.onPackageAdded(InstalledApp("com.x.$it", it)) }
        repo.extendObservation("com.x.due", 0)
        repo.extendObservation("com.x.explicit", 0); repo.setLevel("com.x.explicit", ProtectLevel.STRONG)
        repo.endObservation("com.x.ended")
        assertEquals(listOf("com.x.due"), repo.observationDue().map { it.pkg })
    }
}
