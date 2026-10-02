package com.sentinel.a11y.service

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.sentinel.a11y.core.*
import com.sentinel.a11y.di.A11yRuntime
import com.sentinel.a11y.fakes.*
import com.sentinel.data.Clock
import com.sentinel.data.db.*
import com.sentinel.data.repo.*
import com.sentinel.data.rules.RuleStore
import com.sentinel.data.rules.SubscriptionUpdater
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
abstract class ServiceTestData {
    @get:Rule val temp = TemporaryFolder()
    protected lateinit var context: Application
    protected lateinit var db: SentinelDatabase
    protected lateinit var runtime: A11yRuntime
    protected val time = TestClock(1_700_000_000_000L)
    protected val clock = Clock(time::read)

    // Room 在 data 的运行时依赖中；本任务不能改 build.gradle，反射仅用于建库和 SQL 故障注入。
    @Before fun createData() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        val builder = Class.forName("androidx.room.Room").getMethod(
            "inMemoryDatabaseBuilder", Context::class.java, Class::class.java
        ).invoke(null, context, SentinelDatabase::class.java)
        ReflectionHelpers.callInstanceMethod<Any>(builder, "allowMainThreadQueries")
        val direct = Executor { it.run() }
        for (method in listOf("setQueryExecutor", "setTransactionExecutor")) {
            ReflectionHelpers.callInstanceMethod<Any>(builder, method,
                ReflectionHelpers.ClassParameter.from(Executor::class.java, direct))
        }
        db = ReflectionHelpers.callInstanceMethod(builder, "build")
        dao<GlobalStateDao>("globalStateDao").upsert(GlobalStateEntity())
        for (pkg in listOf(APP, TAOBAO)) {
            dao<AppConfigDao>("appConfigDao").upsert(AppConfigEntity(pkg, pkg, ProtectLevel.STANDARD,
                sensitive = false, firstSeenAt = time.now, observationEndsAt = 0))
            installActivity(pkg)
        }
        runtime = makeRuntime()
    }

    protected fun <T> dao(name: String): T = ReflectionHelpers.callInstanceMethod(db, name)

    protected fun sql(statement: String) {
        val helper = ReflectionHelpers.callInstanceMethod<Any>(db, "getOpenHelper")
        val sqlite = ReflectionHelpers.callInstanceMethod<Any>(helper, "getWritableDatabase")
        ReflectionHelpers.callInstanceMethod<Unit>(sqlite, "execSQL",
            ReflectionHelpers.ClassParameter.from(String::class.java, statement))
    }

    protected fun makeRuntime(runtimeContext: Context = context): A11yRuntime {
        val global = GlobalStateRepository(db, clock)
        val signals = SignalRepository(db, clock)
        val users = UserRuleRepository(dao("userRuleDao"), clock)
        val store = RuleStore(File(temp.root, "rules"), global)
        return A11yRuntime(runtimeContext,
            AppConfigRepository(db, clock, global, signals), OverrideRepository(db, clock),
            JumpExceptionRepository(dao("jumpExceptionDao"), clock), RewardWindowRepository(dao("rewardWindowDao"), clock),
            EventRepository(dao("eventDao"), clock), signals, EngineStatusRepository(dao("engineStatusDao"), clock),
            users, store, SubscriptionUpdater(db, clock, OkHttpClient(), File(temp.root, "cache"), store, users), clock)
    }

    protected fun brain() = A11yBrain(runtime.state,
        RewardedHandler(VolumeKeeper(FakeVolume(), MemoryStore()), time::read), time::read,
        ignored = { setOf(HOME, SYSTEM_UI, IME) }, launchers = { setOf(HOME) }, labelToPkg = { null })

    protected fun installActivity(pkg: String): ComponentName {
        val component = ComponentName(pkg, "$pkg.MainActivity")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(component)
            addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            })
        }
        return component
    }

    @After fun closeData() {
        if (::db.isInitialized) ReflectionHelpers.callInstanceMethod<Unit>(db, "close")
    }
}
