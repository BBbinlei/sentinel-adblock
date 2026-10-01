package com.sentinel.data

import android.content.Context
import androidx.room.Room
import com.sentinel.data.db.*
import com.sentinel.data.policy.EffectivePolicy
import com.sentinel.rules.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseTest {
    private val now = 1000L
    private fun config() = AppConfigEntity("pkg", "App", null, sensitive = false, firstSeenAt = 0, observationEndsAt = 0)
    private fun resolve(cfg: AppConfigEntity? = config(), global: GlobalStateEntity = GlobalStateEntity()) =
        EffectivePolicy.resolve(cfg, "pkg", global, now)

    @Test fun `UT-DA-1-01 global disabled and paused`() {
        assertEquals(ProtectLevel.OFF, resolve(global = GlobalStateEntity(enabled = false)).level)
        assertEquals(ProtectLevel.OFF, resolve(global = GlobalStateEntity(pausedUntil = now + 1)).level)
        assertEquals(ProtectLevel.STANDARD, resolve(global = GlobalStateEntity(pausedUntil = now)).level)
    }
    @Test fun `UT-DA-1-02 temporary allow`() {
        assertEquals(ProtectLevel.OFF, resolve(config().copy(level = ProtectLevel.STRONG, tempAllowUntil = now + 1)).level)
        assertEquals(ProtectLevel.STANDARD, resolve(config().copy(tempAllowUntil = now)).level)
    }
    @Test fun `UT-DA-1-03 explicit level wins over sensitivity`() {
        ProtectLevel.entries.forEach { assertEquals(it, resolve(config().copy(level = it, sensitive = true)).level) }
    }
    @Test fun `UT-DA-1-04 sensitive default`() { assertEquals(ProtectLevel.OFF, resolve(config().copy(sensitive = true)).level) }
    @Test fun `UT-DA-1-05 default config`() {
        assertEquals(resolve(), resolve(null))
        val result = resolve(null)
        assertEquals(ProtectLevel.STANDARD, result.level)
        assertTrue(result.splash && result.shake && result.jumpBack && result.notify)
        assertFalse(result.limitOverlay || result.denyClipboard)
        assertEquals(RewardedMode.SILENT, result.rewarded)
    }
    @Test fun `UT-DA-1-06 observation awaits guard even after deadline`() {
        assertTrue(resolve(config().copy(observationEndsAt = 1)).observing)
        assertFalse(resolve(config().copy(observationEndsAt = 1, level = ProtectLevel.STANDARD)).observing)
        assertFalse(resolve().observing)
        assertFalse(resolve(null).observing)
    }
    @Test fun `UT-DA-1-07 all entity round trips`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SentinelDatabase::class.java).build()
        try {
            val app = config().copy(level = ProtectLevel.STRONG, rewarded = RewardedMode.ASK, tempAllowUntil = 2000)
            db.appConfigDao().upsert(app); assertEquals(app, db.appConfigDao().get("pkg"))
            val global = GlobalStateEntity(pausedUntil = 42, ruleVersion = 8)
            db.globalStateDao().upsert(global); assertEquals(global, db.globalStateDao().get())
            val sub = SubscriptionEntity(1, "test", "https://example.com", SubscriptionFormat.HOSTS, RuleLevel.STRONG, DomainTag.TRACKER)
            db.subscriptionDao().upsert(sub); assertEquals(listOf(sub), db.subscriptionDao().all())
            val user = UserRuleEntity("r", "[]", RuleOrigin.MANUAL, 1)
            db.userRuleDao().upsert(user); assertEquals(listOf(user), db.userRuleDao().all())
            val event = EventEntity(1, 2, "pkg", EventKind.DNS_BLOCKED, "r", "detail")
            db.eventDao().insert(event); assertEquals(listOf(event), db.eventDao().all())
            val signal = SignalEntity(1, 2, "pkg", SignalKind.RETRY_STORM, "r")
            db.signalDao().insert(signal); assertEquals(listOf(signal), db.signalDao().all())
            val override = RuleOverrideEntity("pkg", "r", OverrideState.PINNED, "reason", 3)
            db.overrideDao().upsert(override); assertEquals(listOf(override), db.overrideDao().all())
            val op = OpLogEntity(1, 2, "op", "pkg", "old", "cmd", true, "ok")
            db.opLogDao().insert(op); assertEquals(listOf(op), db.opLogDao().all())
            val jump = JumpExceptionEntity("pkg", "target")
            db.jumpExceptionDao().upsert(jump); assertEquals(listOf(jump), db.jumpExceptionDao().all())
            val reward = RewardWindowEntity("pkg", 123)
            db.rewardWindowDao().upsert(reward); assertEquals(listOf(reward), db.rewardWindowDao().all())
            val status = EngineStatusEntity(EngineId.VPN, EngineState.DEGRADED, "limited", 4)
            db.engineStatusDao().upsert(status); assertEquals(listOf(status), db.engineStatusDao().all())
        } finally { db.close() }
    }
    @Test fun `UT-DA-1-08 all enums stored by name`() {
        val c = Converters()
        ProtectLevel.entries.forEach { assertEquals(it, c.toProtectLevel(c.fromProtectLevel(it))) }
        RewardedMode.entries.forEach { assertEquals(it, c.toRewardedMode(c.fromRewardedMode(it))) }
        SubscriptionFormat.entries.forEach { assertEquals(it, c.toSubscriptionFormat(c.fromSubscriptionFormat(it))) }
        SignalKind.entries.forEach { assertEquals(it, c.toSignalKind(c.fromSignalKind(it))) }
        OverrideState.entries.forEach { assertEquals(it, c.toOverrideState(c.fromOverrideState(it))) }
        EngineId.entries.forEach { assertEquals(it, c.toEngineId(c.fromEngineId(it))) }
        EngineState.entries.forEach { assertEquals(it, c.toEngineState(c.fromEngineState(it))) }
        RuleOrigin.entries.forEach { assertEquals(it, c.toRuleOrigin(c.fromRuleOrigin(it))) }
        RuleLevel.entries.forEach { assertEquals(it, c.toRuleLevel(c.fromRuleLevel(it))) }
        DomainTag.entries.forEach { assertEquals(it, c.toDomainTag(c.fromDomainTag(it))) }
        EventKind.entries.forEach { assertEquals(it, c.toEventKind(c.fromEventKind(it))) }
    }
}
