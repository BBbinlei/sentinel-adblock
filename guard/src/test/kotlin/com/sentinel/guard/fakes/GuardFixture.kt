package com.sentinel.guard.fakes

import android.content.Context
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.sentinel.data.Clock
import com.sentinel.data.apps.AppRegistry
import com.sentinel.data.apps.InstalledApp
import com.sentinel.data.db.*
import com.sentinel.data.di.dataModule
import com.sentinel.data.repo.*
import com.sentinel.guard.di.guardModule
import com.sentinel.guard.runtime.*
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RuntimeEnvironment
import kotlin.test.*

class MutableClock(var time: Long = 1_800_000_000_000L) : Clock {
    override fun now(): Long = time
}

class RecordingNotifier : GuardNotifier {
    data class Disabled(val pkg: String, val label: String, val ids: List<String>, val reason: String)
    data class NoRule(val pkg: String, val label: String, val reason: String)
    val disabled = mutableListOf<Disabled>()
    val noRule = mutableListOf<NoRule>()
    val extended = mutableListOf<Pair<String, String>>()
    var failNextDisabled = false
    override fun notifyDisabled(pkg: String, label: String, ruleIds: List<String>, reason: String) {
        if (failNextDisabled) {
            failNextDisabled = false
            throw IllegalStateException("injected notification failure")
        }
        disabled += Disabled(pkg, label, ruleIds.toList(), reason)
    }
    override fun notifyNoRule(pkg: String, label: String, reason: String) {
        noRule += NoRule(pkg, label, reason)
    }
    override fun notifyObservationExtended(pkg: String, label: String) { extended += pkg to label }
}

@OptIn(ExperimentalCoroutinesApi::class)
abstract class GuardFixture {
    protected val pkg = "com.example.reader"
    protected val label = "阅读器"
    protected val rule = "dns:ads.example.test"
    protected val clock = MutableClock()
    protected val notifier = RecordingNotifier()
    protected val scheduler = TestCoroutineScheduler()
    protected val dispatcher = StandardTestDispatcher(scheduler)
    protected lateinit var context: Context
    protected lateinit var db: SentinelDatabase
    protected lateinit var koin: Koin
    protected val apps get() = koin.get<AppConfigRepository>()
    protected val signals get() = koin.get<SignalRepository>()
    protected val events get() = koin.get<EventRepository>()
    protected val overrides get() = koin.get<OverrideRepository>()
    protected val registry get() = koin.get<AppRegistry>()
    protected val runner get() = koin.get<GuardRunner>()

    @Before fun setUpFixture() {
        Dispatchers.setMain(dispatcher)
        context = RuntimeEnvironment.getApplication()
        val direct = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, SentinelDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor(direct).setTransactionExecutor(direct).build()
        // Bypass production first-install subscription downloads in this isolated database.
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR IGNORE INTO global_state(id, enabled, pausedUntil, ruleVersion) VALUES(0, 1, NULL, 0)"
        )
        koin = startKoin {
            allowOverride(true)
            androidContext(context)
            modules(dataModule, guardModule, module {
                single<SentinelDatabase> { db }
                single<Clock> { clock }
                single<GuardNotifier> { notifier }
            })
        }.koin
    }

    @After fun tearDownFixture() {
        stopKoin()
        db.close()
        Dispatchers.resetMain()
    }

    protected suspend fun register(pkg: String = this.pkg, label: String = this.label, fresh: Boolean = false) {
        if (fresh) registry.onPackageAdded(InstalledApp(pkg, label))
        else registry.syncInstalled(listOf(InstalledApp(pkg, label)), initial = true)
    }

    protected suspend fun disabled(pkg: String = this.pkg) = overrides.observeDisabled().first()[pkg].orEmpty()
    protected fun overrideState(pkg: String = this.pkg, ruleId: String = rule): String? =
        db.openHelper.readableDatabase.query(
            "SELECT state FROM rule_overrides WHERE pkg = ? AND ruleId = ?", arrayOf(pkg, ruleId)
        ).use { if (it.moveToFirst()) it.getString(0) else null }

    protected suspend fun assertHandled(pkg: String = this.pkg, count: Int = 1) {
        assertEquals(emptyList(), signals.observeUnhandled().first().filter { it.pkg == pkg })
        assertEquals(count, signals.countSince(pkg, SignalKind.entries.toSet(), 0))
        db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM signals WHERE pkg = ? AND handled = 1", arrayOf(pkg)
        ).use { assertTrue(it.moveToFirst()); assertEquals(count, it.getInt(0)) }
    }

    protected suspend fun runObservation(): ListenableWorker.Result =
        TestListenableWorkerBuilder<ObservationWorker>(context).build().doWork()
}
