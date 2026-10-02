package com.sentinel.app.fakes

import androidx.room.Room
import androidx.room.withTransaction
import com.sentinel.data.Clock
import com.sentinel.data.apps.AppRegistry
import com.sentinel.data.db.*
import com.sentinel.data.repo.*
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import kotlin.coroutines.Continuation
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment

class FakeClock(var time: Long = 1_800_000_000_000L) : Clock {
    override fun now() = time
}

/** Synchronous fixture access; reads and writes always reach the real Room database. */
class Rows<T>(private val read: suspend () -> T, private val write: suspend (T) -> Unit) {
    var value: T
        get() = runBlocking { read() }
        set(value) { runBlocking { write(value) } }
}

class FakeData(val clock: FakeClock = FakeClock()) : AutoCloseable {
    data class DaoCall(val dao: String, val method: String, val args: List<Any?>)
    val calls = mutableListOf<DaoCall>()
    private val database = lazy {
        val direct = Executor { it.run() }
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SentinelDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor(direct).setTransactionExecutor(direct).build()
    }
    val db: SentinelDatabase get() = database.value

    // Keep fixture writes out of the production-call ledger.
    private val appDao by lazy { db.appConfigDao() }
    private val overrideDao by lazy { db.overrideDao() }
    private val userDao by lazy { db.userRuleDao() }
    private val recording = lazy {
        record(AppConfigDao::class.java, appDao)
        record(OverrideDao::class.java, overrideDao, "RuleOverrideDao")
        record(UserRuleDao::class.java, userDao)
    }

    /** Record actual calls and delegate every operation to Room, including suspend calls. */
    private fun <T : Any> record(type: Class<T>, dao: T, name: String = type.simpleName) {
        val proxy = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, raw ->
            calls += DaoCall(name, method.name, raw.orEmpty().filterNot { it is Continuation<*> })
            try { method.invoke(dao, *raw.orEmpty()) }
            catch (e: InvocationTargetException) { throw e.targetException }
        }
        // Room's generated database caches each DAO. Replace that cache with a forwarding spy.
        val field = db.javaClass.getDeclaredField("_" + type.simpleName.replaceFirstChar { it.lowercase() })
        field.isAccessible = true
        field.set(db, lazy { type.cast(proxy) })
    }

    private fun <T> rows(table: String, read: suspend () -> List<T>, insert: suspend (T) -> Unit) =
        Rows(read) { values -> db.withTransaction {
            db.openHelper.writableDatabase.execSQL("DELETE FROM $table")
            values.forEach { insert(it) }
        } }

    val globalRows = Rows({ db.globalStateDao().get() ?: GlobalStateEntity() }, { db.globalStateDao().upsert(it) })
    val appsRows = rows("app_config", { appDao.all() }, { appDao.upsert(it) })
    val eventRows = rows("events", { db.eventDao().all() }, { db.eventDao().insert(it) })
    val statusRows = rows("engine_status", { db.engineStatusDao().all() }, { db.engineStatusDao().upsert(it) })
    val overrideRows = rows("rule_overrides", { overrideDao.all() }, { overrideDao.upsert(it) })
    val signalRows = rows("signals", { db.signalDao().all() }, { db.signalDao().insert(it) })
    val userRows = rows("user_rules", { userDao.all() }, { userDao.upsert(it) })
    val logRows = rows("op_log", { db.opLogDao().all() }, { db.opLogDao().insert(it) })

    val global by lazy { GlobalStateRepository(db, clock) }
    val signals by lazy { SignalRepository(db, clock) }
    val apps by lazy { recording.value; AppConfigRepository(db, clock, global, signals) }
    val events by lazy { EventRepository(db.eventDao(), clock) }
    val statuses by lazy { EngineStatusRepository(db.engineStatusDao(), clock) }
    val overrides by lazy { recording.value; OverrideRepository(db, clock) }
    val users by lazy { recording.value; UserRuleRepository(db.userRuleDao(), clock) }
    val logs by lazy { OpLogRepository(db.opLogDao(), clock) }
    val registry by lazy { AppRegistry(db, clock) }

    fun app(pkg: String = "com.example.reader", label: String = "阅读器", sensitive: Boolean = false,
        observationEndsAt: Long = 0) = AppConfigEntity(pkg = pkg, label = label, level = null,
        sensitive = sensitive, firstSeenAt = clock.now(), observationEndsAt = observationEndsAt)

    override fun close() { if (database.isInitialized()) db.close() }
}
