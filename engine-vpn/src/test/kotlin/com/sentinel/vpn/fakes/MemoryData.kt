package com.sentinel.vpn.fakes

import androidx.room.Room
import androidx.room.RoomDatabase
import com.sentinel.data.Clock
import com.sentinel.data.db.*
import com.sentinel.data.repo.*
import com.sentinel.data.rules.*
import com.sentinel.rules.domain.DomainCompiler
import com.sentinel.rules.model.DnsRule
import java.io.Closeable
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.first
import org.robolectric.RuntimeEnvironment

/** 真正的 Room 内存 data，不继承 PLAN 中 final 的仓库，不伪造产品类。 */
class MemoryData(val clock: Clock, context: CoroutineContext) : Closeable {
    val database: SentinelDatabase = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SentinelDatabase::class.java)
        .allowMainThreadQueries().setQueryCoroutineContext(context).build()
    private val room: RoomDatabase = database
    private val directory = Files.createTempDirectory("pending-vpn-rules").toFile()
    private val repositories = mutableMapOf<Class<*>, Any>()
    val global = repository(GlobalStateRepository::class.java)
    val apps = repository(AppConfigRepository::class.java)
    val overrides = repository(OverrideRepository::class.java)
    val rewards = repository(RewardWindowRepository::class.java)
    val events = repository(EventRepository::class.java)
    val signals = repository(SignalRepository::class.java)
    val status = repository(EngineStatusRepository::class.java)
    val rules = RuleStore(directory, global)

    // PLAN 允许「对应 DAO（或 SentinelDatabase）+ Clock」，却未定构造顺序和 DAO 方法名。
    // 只在这一处按类型接线；找不到唯一构造就明确失败，不用 Unsafe 或 mock 框架。
    private fun <T : Any> repository(type: Class<T>): T {
        repositories[type]?.let { return type.cast(it) }
        val options = type.constructors.filter { ctor -> ctor.parameterTypes.all { arg ->
            arg.isInstance(database) || arg == Clock::class.java || arg.name.startsWith("com.sentinel.data.repo.") || database.javaClass.methods.any { it.parameterCount == 0 && arg.isAssignableFrom(it.returnType) && arg.name.startsWith("com.sentinel.data.") }
        } }
        require(options.size == 1) { "${type.name}: 需唯一的 (DAO/Database, Clock) 构造；实际 ${options.size}" }
        val args = options.single().parameterTypes.map { arg -> when {
            arg.isInstance(database) -> database
            arg == Clock::class.java -> clock
            arg.name.startsWith("com.sentinel.data.repo.") -> repository(arg)
            else -> database.javaClass.methods.filter { it.parameterCount == 0 && arg.isAssignableFrom(it.returnType) }.distinctBy { it.name }.single().invoke(database)
        } }.toTypedArray()
        return type.cast(options.single().newInstance(*args)).also { repositories[type] = it }
    }
    fun sql(statement: String, vararg args: Any?) = room.openHelper.writableDatabase.execSQL(statement, args)
    suspend fun initialize(vararg rules: DnsRule) {
        sql("INSERT OR IGNORE INTO global_state (id, enabled, pausedUntil, ruleVersion) VALUES (0, 1, NULL, 0)")
        install(*rules)
    }
    suspend fun install(vararg rules: DnsRule) = this.rules.install(BuiltRules(DomainCompiler.compile(rules.toList()), "[]", "[]", emptyMap()))
    fun app(pkg: String = "test.app", level: ProtectLevel? = ProtectLevel.STANDARD, sensitive: Boolean = false, observing: Boolean = false) {
        sql("""INSERT OR REPLACE INTO app_config (pkg, label, level, splash, rewarded, shake, jumpBack, notify,
            limitOverlay, denyClipboard, sensitive, tempAllowUntil, firstSeenAt, observationEndsAt)
            VALUES (?, ?, ?, 1, 'SILENT', 1, 1, 1, 0, 0, ?, NULL, ?, ?)""",
            pkg, pkg, level?.name, if (sensitive) 1 else 0, clock.now(), if (observing) clock.now() + 259_200_000L else 0L)
        room.invalidationTracker.refreshAsync()
    }
    suspend fun eventRows() = events.observeRecent(com.sentinel.rules.model.EventKind.entries.toSet(), 10000).first()
    suspend fun signalRows() = signals.observeUnhandled().first()
    suspend fun vpnStatus() = status.observeAll().first()[EngineId.VPN]
    fun failEventWrites(on: Boolean) {
        if (on) sql("CREATE TRIGGER IF NOT EXISTS test_fail_events BEFORE INSERT ON events BEGIN SELECT RAISE(ABORT, 'injected event write failure'); END")
        else sql("DROP TRIGGER IF EXISTS test_fail_events")
    }
    override fun close() { database.close(); check(directory.deleteRecursively()) { "无法清理 $directory" } }
}
