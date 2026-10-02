package com.sentinel.regression.contract.fakes

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.sentinel.a11y.core.*
import com.sentinel.a11y.di.a11yModule
import com.sentinel.a11y.di.A11yEntry
import com.sentinel.a11y.di.A11yRuntime
import com.sentinel.a11y.service.SentinelAccessibilityService
import com.sentinel.data.Clock
import com.sentinel.data.apps.*
import com.sentinel.data.db.*
import com.sentinel.data.di.dataModule
import com.sentinel.data.repo.*
import com.sentinel.data.rules.*
import com.sentinel.guard.di.guardModule
import com.sentinel.notify.core.*
import com.sentinel.notify.di.notifyModule
import com.sentinel.notify.di.NotifyEntry
import com.sentinel.notify.di.NotifyRuntime
import com.sentinel.notify.service.SentinelNotificationListener
import com.sentinel.system.crash.DropboxCrashWorker
import com.sentinel.system.di.systemModule
import com.sentinel.system.shell.*
import com.sentinel.vpn.di.vpnModule
import com.sentinel.vpn.dns.UpstreamResolver
import com.sentinel.vpn.service.TunFactory
import com.sentinel.vpn.service.VpnController
import com.sentinel.vpn.tun.*
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.*
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
abstract class ContractFixture {
    protected val pkg = "com.example.reader"
    protected val label = "阅读器"
    protected val otherPkg = "com.example.other"
    protected val launcher = "com.example.launcher"
    protected val clock = MutableClock()
    protected val scheduler = TestCoroutineScheduler()
    protected val dispatcher = StandardTestDispatcher(scheduler)
    protected lateinit var context: Context
    protected lateinit var db: SentinelDatabase
    protected lateinit var ruleDir: File
    protected lateinit var koin: Koin
    protected val tunFactory = RecordingTunFactory()
    protected val upstream = RecordingUpstream()
    protected val packageResolver = MutablePackageResolver(pkg)
    protected val shell = FixtureShell()
    protected val api = ReadyApi()
    protected var vpn: VpnController? = null
    private var capturedDecisions: DecisionSource? = null
    private var a11yService: ServiceController<SentinelAccessibilityService>? = null
    private var notifyService: ServiceController<SentinelNotificationListener>? = null
    protected val apps get() = koin.get<AppConfigRepository>()
    protected val signals get() = koin.get<SignalRepository>()
    protected val status get() = koin.get<EngineStatusRepository>()
    protected val global get() = koin.get<GlobalStateRepository>()
    protected val store get() = koin.get<RuleStore>()
    protected val users get() = koin.get<UserRuleRepository>()
    protected val updater get() = koin.get<SubscriptionUpdater>()

    @Before fun setUpFixture() {
        Dispatchers.setMain(dispatcher)
        context = RuntimeEnvironment.getApplication()
        val direct = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, SentinelDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor(direct).setTransactionExecutor(direct).build()
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR IGNORE INTO global_state(id, enabled, pausedUntil, ruleVersion) VALUES(0, 1, NULL, 0)"
        )
        ruleDir = File(context.cacheDir, "contract-rules").apply { mkdirs() }
        koin = startKoin {
            allowOverride(true)
            androidContext(context)
            modules(dataModule, vpnModule, a11yModule, notifyModule, systemModule, guardModule, module {
                single<SentinelDatabase> { db }
                single<Clock> { clock }
                single<RuleStore> { RuleStore(ruleDir, get()) }
                single<TunFactory> { tunFactory }
                single<UpstreamResolver> { upstream }
                single<PackageResolver> { packageResolver }
                single<ShizukuApi> { api }
                single<com.sentinel.a11y.core.A11yState> { get<com.sentinel.a11y.di.A11yRuntime>().state }
                factory<DecisionSource> { checkNotNull(capturedDecisions) { "VpnController not started" } }
                single<Shell> { shell }
                single<ShizukuGateway> { ShizukuGateway(api) { shell.binder() } }
            })
        }.koin
    }

    @After fun tearDownFixture() {
        a11yService?.destroy()
        notifyService?.destroy()
        tunFactory.close()
        stopKoin()
        db.close()
        ruleDir.deleteRecursively()
        Dispatchers.resetMain()
    }

    protected fun contractTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally {
            try { stopReaders() } finally { tunFactory.close() }
            runCurrent()
        }
    }

    protected suspend fun registerApps() {
        koin.get<AppRegistry>().syncInstalled(listOf(InstalledApp(pkg, label),
            InstalledApp(otherPkg, "其他应用")), initial = true)
        // The undo receiver can launch its synthetic target without a physical device.
        for (name in listOf(pkg, otherPkg)) {
            val application = ApplicationInfo().apply { packageName = name; enabled = true }
            val activity = ActivityInfo().apply {
                packageName = name; this.name = "$name.Main"; applicationInfo = application
                enabled = true; exported = true
            }
            shadowOf(context.packageManager).addPackage(PackageInfo().apply {
                packageName = name; applicationInfo = application; activities = arrayOf(activity)
            })
            shadowOf(context.packageManager).addResolveInfoForIntent(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(name),
                ResolveInfo().apply { activityInfo = activity })
        }
    }

    protected suspend fun startVpn(scope: CoroutineScope) {
        // The service constructs a fresh controller for each lifecycle; stop is terminal.
        vpn = VpnController(scope, koin.get(), context.packageName, koin.get(), koin.get(), koin.get(), koin.get(),
            koin.get<RuleStore>(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get<Clock>(), { },
            { source -> source.also { capturedDecisions = it } })
        vpn!!.start()
    }

    protected suspend fun startReaders(scope: CoroutineScope) {
        startVpn(scope)
        // Production ModuleEntry owns the ruleVersion subscriptions, not the services.
        A11yEntry().start(context, scope, koin)
        NotifyEntry().start(context, scope, koin)
        a11yService = Robolectric.buildService(SentinelAccessibilityService::class.java).create()
        val connected = SentinelAccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        connected.isAccessible = true
        connected.invoke(a11yService!!.get())
        notifyService = Robolectric.buildService(SentinelNotificationListener::class.java).create()
        notifyService!!.get().onListenerConnected()
        awaitCondition {
            val current = status.observeAll().first()
            listOf(EngineId.A11Y, EngineId.NOTIFY).all { current[it]?.state == EngineState.RUNNING }
        }
    }

    protected suspend fun stopReaders() {
        vpn?.stop("contract normal stop")
        a11yService?.get()?.onUnbind(Intent())
        a11yService?.destroy()
        a11yService = null
        notifyService?.get()?.onListenerDisconnected()
        notifyService?.destroy()
        notifyService = null
        if (vpn != null) awaitCondition {
            val current = status.observeAll().first()
            listOf(EngineId.VPN, EngineId.A11Y, EngineId.NOTIFY).all { current[it]?.state == EngineState.STOPPED }
        }
    }

    protected suspend fun newBrain(): A11yBrain {
        koin.get<A11yRuntime>().prefetch(listOf(pkg, otherPkg))
        val volume = MemoryVolume()
        val rewarded = RewardedHandler(VolumeKeeper(volume, volume), clock::now)
        return A11yBrain(koin.get<A11yState>(), rewarded, clock::now,
            ignored = { setOf(context.packageName, "com.android.systemui") },
            launchers = { setOf(launcher) }, labelToPkg = { if (it.trim() == label) pkg else null })
    }

    protected suspend fun newNotifyEngine(): NotifyEngine = koin.get<NotifyRuntime>().engineFor(pkg)

    protected suspend fun awaitCondition(condition: suspend () -> Boolean) = withContext(Dispatchers.IO) {
        // Only synchronize real IO callbacks; contract deadlines use MutableClock.
        withTimeout(5_000) {
            while (!condition()) { scheduler.runCurrent(); yield() }
        }
    }

    protected suspend fun persistSignals(actions: List<A11yAction>) {
        for (action in actions.filterIsInstance<A11yAction.Signal>()) {
            val signal = action.signal
            signals.emit(signal.pkg, signal.kind, detail = signal.detail)
        }
    }

    protected suspend fun collectDropbox(): ListenableWorker.Result {
        // Worker reads platform time rather than Clock; Robolectric supplies the same controlled time.
        check(android.os.SystemClock.setCurrentTimeMillis(clock.now()))
        return TestListenableWorkerBuilder<DropboxCrashWorker>(context).build().doWork()
    }

    protected suspend fun allSignals(): List<SignalEntity> = signals.observeUnhandled().first()
}
