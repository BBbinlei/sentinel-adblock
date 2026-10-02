package com.sentinel.regression.contract

import android.content.Intent
import androidx.work.ListenableWorker
import com.sentinel.a11y.core.*
import com.sentinel.a11y.di.A11yRuntime
import com.sentinel.a11y.service.A11yActionReceiver
import com.sentinel.a11y.service.A11yActions
import com.sentinel.data.contract.*
import com.sentinel.data.db.*
import com.sentinel.data.repo.RewardWindowRepository
import com.sentinel.guard.policy.*
import com.sentinel.notify.core.*
import com.sentinel.regression.contract.fakes.ContractFixture
import com.sentinel.rules.domain.DomainCompiler
import com.sentinel.rules.model.*
import com.sentinel.rules.ui.SnapshotNode
import com.sentinel.system.crash.DropboxParser
import com.sentinel.system.drift.SystemStatusReporter
import com.sentinel.vpn.decide.*
import com.sentinel.vpn.health.*
import com.sentinel.vpn.tun.DecisionSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class ContractTest : ContractFixture() {
    private suspend fun deliverUndo(intent: Intent) {
        val receiver = A11yActionReceiver()
        context.registerReceiver(receiver, android.content.IntentFilter(intent.action), android.content.Context.RECEIVER_NOT_EXPORTED)
        try {
            context.sendBroadcast(intent)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            val pending = checkNotNull(shadowOf(receiver).originalPendingResult)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                shadowOf(pending).future.get(5, java.util.concurrent.TimeUnit.SECONDS)
            }
        } finally { context.unregisterReceiver(receiver) }
    }
    private fun dns(domain: String, tag: DomainTag = DomainTag.AD) =
        DnsRule("dns:$domain", domain, tag, RuleLevel.STANDARD, "contract")
    private fun clicked() = UiInput.Clicked(pkg, "看视频领奖励", null, null,
        "android.widget.Button", clock.now())
    private suspend fun rewardClickActions(): List<A11yAction> {
        val brain = newBrain()
        brain.onInput(UiInput.WindowChanged(pkg, "Main", clock.now()), null)
        return brain.onInput(clicked(), null)
    }
    private fun verdict(domain: String, pkg: String = this.pkg): DnsVerdict {
        val source = koin.get<DecisionSource>()
        return DnsDecider.decide(domain, source.matcher(), source.context(pkg)).verdict
    }
    private suspend fun installRewardRules() {
        store.install(com.sentinel.data.rules.BuiltRules(
            DomainCompiler.compile(listOf(dns("sdk.contract.test", DomainTag.AD_SDK), dns("ad.contract.test"))),
            "[]", "[]", emptyMap()))
    }

    @Test fun MT_CT_01_reward_window_is_scoped_expiring_and_fail_closed() = contractTest {
        registerApps()
        installRewardRules()
        startReaders(backgroundScope)
        runCurrent()
        assertEquals(DnsVerdict.BLOCK, verdict("sdk.contract.test"))
        val windows = koin.get<RewardWindowRepository>()
        for (mode in listOf(RewardedMode.SILENT, RewardedMode.ASK)) {
            apps.setRewarded(pkg, mode)
            clock.time += A11yRuntime.TTL_MS + 1
            runCurrent()
            val actions = rewardClickActions()
            val open = actions.filterIsInstance<A11yAction.OpenRewardWindow>().single()
            assertEquals(pkg, open.pkg)
            windows.open(open.pkg)
            runCurrent()
            assertEquals(clock.now() + RewardWindowContract.TTL_MS, windows.observeOpen().first()[pkg])
            assertEquals(DnsVerdict.FORWARD, verdict("sdk.contract.test"))
            assertEquals(DnsVerdict.BLOCK, verdict("ad.contract.test"))
            assertEquals(DnsVerdict.BLOCK, verdict("sdk.contract.test", otherPkg))
            // A second click replaces the deadline, it does not add another TTL to the old one.
            clock.time += 1_000
            val again = rewardClickActions().filterIsInstance<A11yAction.OpenRewardWindow>().single()
            windows.open(again.pkg)
            runCurrent()
            assertEquals(clock.now() + RewardWindowContract.TTL_MS, windows.observeOpen().first()[pkg])
            clock.time += RewardWindowContract.TTL_MS + 1
            runCurrent()
            assertEquals(DnsVerdict.BLOCK, verdict("sdk.contract.test"))
        }
        apps.setRewarded(pkg, RewardedMode.BLOCK)
        clock.time += A11yRuntime.TTL_MS + 1
        runCurrent()
        val deadline = windows.observeOpen().first()[pkg]
        assertEquals(emptyList(), rewardClickActions()
            .filterIsInstance<A11yAction.OpenRewardWindow>())
        assertEquals(deadline, windows.observeOpen().first()[pkg])
        assertEquals(DnsVerdict.BLOCK, verdict("sdk.contract.test"))
        // Re-open before the fault so preserving a stale active window cannot pass the assertion.
        apps.setRewarded(pkg, RewardedMode.SILENT)
        windows.open(pkg)
        runCurrent()
        assertEquals(DnsVerdict.FORWARD, verdict("sdk.contract.test"))
        // Re-subscribe with only the reward-window table unavailable; other data remains readable.
        vpn!!.stop("inject reward table failure")
        db.openHelper.writableDatabase.execSQL("DROP TABLE reward_windows")
        startVpn(backgroundScope)
        runCurrent()
        assertFalse(koin.get<DecisionSource>().context(pkg).rewardWindowOpen)
        assertEquals(DnsVerdict.BLOCK, verdict("sdk.contract.test"))
        assertEquals(DnsVerdict.BLOCK, verdict("ad.contract.test"))
    }

    @Test fun MT_CT_02_each_declared_emitter_produces_a_handled_kind_and_off_emits_none() = contractTest {
        registerApps()
        installRewardRules()
        startReaders(backgroundScope)
        runCurrent()
        val expectedEmitters = mapOf(SignalKind.USER_UNDO to EngineId.A11Y,
            SignalKind.TEMP_ALLOW to null, SignalKind.RETRY_STORM to EngineId.VPN,
            SignalKind.CRASH_DIALOG to EngineId.A11Y, SignalKind.COLD_START_LOOP to EngineId.A11Y,
            SignalKind.DROPBOX_CRASH to EngineId.SYSTEM)
        assertEquals(SignalKind.entries.toSet(), SignalContract.EMITTERS.keys)
        assertEquals(expectedEmitters, SignalContract.EMITTERS)
        val crashRoot = SnapshotNode(children = listOf(SnapshotNode(text = "${label}已停止运行")))
        val crash = newBrain().onInput(UiInput.WindowChanged("android", null, clock.now()), crashRoot)
        assertEquals(SignalKind.CRASH_DIALOG, crash.filterIsInstance<A11yAction.Signal>().single().signal.kind)
        persistSignals(crash)
        val launches = newBrain()
        val launchActions = mutableListOf<A11yAction>()
        repeat(3) {
            launchActions += launches.onInput(UiInput.WindowChanged("com.example.home$it", "Home", clock.now()), null)
            clock.time += 1_000
            launchActions += launches.onInput(UiInput.WindowChanged(pkg, "Main", clock.now()), null)
            clock.time += 1_000
        }
        assertEquals(listOf(SignalKind.COLD_START_LOOP), launchActions.filterIsInstance<A11yAction.Signal>()
            .map { it.signal.kind })
        persistSignals(launchActions)
        val undo = Intent(A11yActions.JUMP_UNDO).putExtra("source", pkg).putExtra("target", otherPkg)
        deliverUndo(undo)
        runCurrent()
        assertEquals(1, signals.countSince(pkg, setOf(SignalKind.USER_UNDO), 0))
        val before = upstream.calls.get()
        repeat(10) { index ->
            tunFactory.current.query("ad.contract.test", index + 1)
            runCurrent()
        }
        assertEquals(before, upstream.calls.get(), "blocked requests must not reach upstream")
        assertEquals(1, signals.countSince(pkg, setOf(SignalKind.RETRY_STORM), 0))
        // First run establishes the real Worker's last-check time without any crash entries.
        assertEquals(ListenableWorker.Result.success(), collectDropbox())
        clock.time += 2_000
        shell.dropboxOutput = dropboxSample(clock.now())
        val parsed = DropboxParser.parse(shell.dropboxOutput)
        assertEquals(listOf(pkg), parsed.map { it.pkg })
        assertEquals(ListenableWorker.Result.success(), collectDropbox())
        apps.tempAllow(pkg)
        runCurrent()
        val emitted = allSignals()
        assertEquals(SignalKind.entries.toSet(), emitted.map { it.kind }.toSet())
        for (kind in SignalKind.entries) {
            val signal = emitted.single { it.kind == kind }
            assertEquals(pkg, signal.pkg)
            val decision = GuardPolicy.onSignal(signal, emptyList(), 1)
            when (kind) {
                SignalKind.USER_UNDO -> assertNull(decision)
                SignalKind.RETRY_STORM -> assertEquals(Decision.DisableRules(pkg,
                    listOf("dns:ad.contract.test"), "重试风暴"), decision)
                SignalKind.TEMP_ALLOW -> assertEquals(Decision.NoRuleFound(pkg, "你临时放行了该应用"), decision)
                SignalKind.CRASH_DIALOG, SignalKind.DROPBOX_CRASH ->
                    assertEquals(Decision.NoRuleFound(pkg, "应用崩溃"), decision)
                SignalKind.COLD_START_LOOP -> assertEquals(Decision.NoRuleFound(pkg, "应用反复重启"), decision)
            }
        }
        // Repeat every producing path for an explicitly excluded app.
        apps.setLevel(pkg, ProtectLevel.OFF)
        clock.time += 600_001
        runCurrent()
        val counts = SignalKind.entries.associateWith { signals.countSince(pkg, setOf(it), 0) }
        val rowsBeforeOff = allSignals()
        val offCrash = newBrain().onInput(UiInput.WindowChanged("android", null, clock.now()), crashRoot)
        persistSignals(offCrash)
        assertEquals(rowsBeforeOff, allSignals(), "C2: OFF crash signals must not land in Room")
        val offBrain = newBrain()
        val offLaunches = mutableListOf<A11yAction>()
        repeat(3) {
            offLaunches += offBrain.onInput(UiInput.WindowChanged("com.example.home$it", "Home", clock.now()), null)
            clock.time += 1_000
            offLaunches += offBrain.onInput(UiInput.WindowChanged(pkg, "Main", clock.now()), null)
        }
        persistSignals(offLaunches)
        assertEquals(rowsBeforeOff, allSignals(), "C2: OFF cold-start signals must not land in Room")
        deliverUndo(undo)
        val forwardedBefore = upstream.calls.get()
        repeat(10) { tunFactory.current.query("ad.contract.test", 100 + it); runCurrent() }
        assertEquals(forwardedBefore + 10, upstream.calls.get())
        shell.dropboxOutput = dropboxSample(clock.now())
        assertEquals(ListenableWorker.Result.success(), collectDropbox())
        apps.tempAllow(pkg)
        runCurrent()
        for (kind in SignalKind.entries) assertEquals(counts.getValue(kind),
            signals.countSince(pkg, setOf(kind), 0), "OFF must not emit $kind")
        assertEquals(rowsBeforeOff, allSignals(), "OFF paths must leave the signal table unchanged")
        apps.setLevel(otherPkg, ProtectLevel.OFF)
        for (kind in SignalKind.entries) signals.emit(otherPkg, kind, ruleId = "dns:ad.contract.test")
        assertEquals(emptyList(), allSignals().filter { it.pkg == otherPkg },
            "C2: emitting every kind for an OFF app with no history must leave no rows")
    }

    @Test fun MT_CT_03_real_readers_hot_swap_only_after_successful_rebuild() = contractTest {
        registerApps()
        val old = versionRules("old")
        old.forEach { users.add(it, RuleOrigin.MANUAL) }
        updater.rebuildFromCache()
        startReaders(backgroundScope)
        runCurrent()
        assertVersionVisible("old", "new")
        val tunCount = tunFactory.handles.size
        val before = global.get().ruleVersion
        old.forEach { users.delete(it.id) }
        val new = versionRules("new")
        new.forEach { users.add(it, RuleOrigin.MANUAL) }
        assertEquals(before + 1, updater.rebuildFromCache())
        runCurrent()
        assertEquals(before + 1, global.get().ruleVersion)
        assertEquals(tunCount, tunFactory.handles.size, "rule updates must not rebuild the TUN")
        assertVersionVisible("new", "old")
        // A malformed cached user rule makes the real rebuild fail before installation.
        db.openHelper.writableDatabase.execSQL("UPDATE user_rules SET json = ? WHERE id = ?",
            arrayOf("{invalid-json", new.first().id))
        var failure: Exception? = null
        try { updater.rebuildFromCache() } catch (e: Exception) { failure = e }
        assertNotNull(failure, "malformed cache must fail the rebuild")
        runCurrent()
        assertEquals(before + 1, global.get().ruleVersion)
        assertVersionVisible("new", "old")
    }

    @Test fun MT_CT_04_normal_stops_preserve_intent_and_degradation_has_message() = contractTest {
        registerApps()
        installRewardRules()
        startReaders(backgroundScope)
        runCurrent()
        for (engine in listOf(EngineId.VPN, EngineId.A11Y, EngineId.NOTIFY))
            assertEquals(EngineState.RUNNING, status.observeAll().first()[engine]?.state, engine.name)
        stopReaders()
        runCurrent()
        val stopped = status.observeAll().first()
        for (engine in listOf(EngineId.VPN, EngineId.A11Y, EngineId.NOTIFY)) {
            assertEquals(EngineState.STOPPED, stopped[engine]?.state, engine.name)
            assertNotEquals(EngineState.NOT_SETUP, stopped[engine]?.state, engine.name)
        }
        // Public watchdog port: the private service SettingsChecker is outside this fixture.
        val checker = object : EnabledServicesChecker {
            override fun accessibilityEnabled() = false
            override fun notificationListenerEnabled() = false
            override fun userWantsA11y() = stopped[EngineId.A11Y]?.state == EngineState.STOPPED
            override fun userWantsNotify() = stopped[EngineId.NOTIFY]?.state == EngineState.STOPPED
        }
        assertTrue(checker.userWantsA11y())
        assertTrue(checker.userWantsNotify())
        val alerts = mutableListOf<EngineId>()
        ServiceWatchdog(checker, status) { alerts += it }.checkOnce()
        assertEquals(setOf(EngineId.A11Y, EngineId.NOTIFY), alerts.toSet())
        api.ready = false
        koin.get<SystemStatusReporter>().start(backgroundScope)
        runCurrent()
        val degraded = status.observeAll().first().values.filter { it.state == EngineState.DEGRADED }
        assertTrue(degraded.isNotEmpty(), "watchdog or system reporter must report degradation")
        assertEquals(EngineState.DEGRADED, status.observeAll().first()[EngineId.SYSTEM]?.state)
        for (entry in degraded) assertFalse(entry.message.isNullOrBlank(), entry.engine.name)
    }

    private fun versionRules(version: String): List<Rule> = listOf(
        dns("$version.contract.test"),
        UiRule("contract:ui:$version", RuleScope.App(pkg), "[vid=\"contract_$version\"]",
            UiAction.CLICK, UiPhase.ANYTIME, "contract"),
        NotifyRule("contract:notify:$version", pkg, "marketing", listOf(version), "contract"))

    private suspend fun assertVersionVisible(active: String, absent: String) {
        fun posted(word: String) = PostedNotification(pkg, "marketing", word, "推广", false, "key:$word")
        awaitCondition {
            verdict("$active.contract.test") == DnsVerdict.BLOCK &&
                "contract:ui:$active" in koin.get<A11yState>().index().lookup(pkg, "Main").map { it.rule.id } &&
                (newNotifyEngine().onPosted(posted(active)) as? NotifyAction.Cancel)?.ruleId == "contract:notify:$active"
        }
        assertEquals(DnsVerdict.BLOCK, verdict("$active.contract.test"))
        assertEquals(DnsVerdict.FORWARD, verdict("$absent.contract.test"))
        val state = koin.get<A11yState>()
        val ids = state.index().lookup(pkg, "Main").map { it.rule.id }
        assertTrue("contract:ui:$active" in ids)
        assertFalse("contract:ui:$absent" in ids)
        val brain = newBrain()
        val activeRoot = SnapshotNode(viewId = "$pkg:id/contract_$active", text = "完成", clickable = true)
        val clicks = brain.onInput(UiInput.WindowChanged(pkg, "Main", clock.now()), activeRoot)
            .filterIsInstance<A11yAction.Click>()
        assertEquals("contract:ui:$active", clicks.single().ruleId)
        val absentRoot = SnapshotNode(viewId = "$pkg:id/contract_$absent", text = "完成", clickable = true)
        assertEquals(emptyList(), newBrain().onInput(UiInput.WindowChanged(pkg, "Main", clock.now()), absentRoot)
            .filterIsInstance<A11yAction.Click>())
        val notify = newNotifyEngine()
        assertEquals("contract:notify:$active", assertIs<NotifyAction.Cancel>(notify.onPosted(posted(active))).ruleId)
        assertNull(notify.onPosted(posted(absent)))
    }

    private fun dropboxSample(ts: Long): String {
        val formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(ts))
        // Synthetic, privacy-free dumpsys fixture, not a claimed capture from a device.
        return """
            $formatted data_app_crash (text, 512 bytes)
            Process: $pkg
            PID: 1234
            Flags: 0x0
            Package: $pkg v1 (1.0)
            Foreground: Yes
            Build: contract/test
            java.lang.IllegalStateException: synthetic contract crash
                at com.example.reader.Main.onCreate(Main.kt:1)
        """.trimIndent()
    }
}
