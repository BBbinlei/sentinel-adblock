package com.sentinel.system.fakes

import androidx.room.Room
import com.sentinel.data.Clock
import com.sentinel.data.db.*
import com.sentinel.data.repo.*
import com.sentinel.system.ops.*
import com.sentinel.system.profile.*
import com.sentinel.system.shell.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import kotlin.test.assertEquals

class ScriptedShell : Shell {
    val commands = mutableListOf<String>()
    private val replies = ArrayDeque<Pair<String, ExecResult>>()
    fun expect(command: String, stdout: String = "", exitCode: Int = 0, stderr: String = "") {
        replies.addLast(command to ExecResult(exitCode, stdout, stderr))
    }
    override suspend fun exec(cmd: String): ExecResult {
        commands += cmd
        val (expected, result) = replies.removeFirst()
        assertEquals(expected, cmd, "Shell command order")
        return result
    }
    fun assertConsumed() = assertEquals(0, replies.size, "Unused shell replies")
}


fun settingOp(id: String = "recommend", key: String = id, verified: Boolean = true) = ProfileOp(
    id = id, trick = 1, title = id, kind = OpKind.SETTING, verified = verified,
    apply = "settings put secure $key 0", revert = "settings put secure $key {before}",
    probe = "settings get secure $key", appliedRegex = "^0$",
)

// data 的仓库为具体类，PLAN 没有可替换接口。边界冲突与构造假设见 README。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MemoryDataTest {
    protected lateinit var db: SentinelDatabase
    protected lateinit var logs: OpLogRepository
    protected lateinit var events: EventRepository
    protected lateinit var configs: AppConfigRepository
    protected lateinit var statuses: EngineStatusRepository
    protected val clock = Clock { 1_700_000_000_000L }

    @Before fun createMemoryData() {
        Dispatchers.setMain(StandardTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SentinelDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Executor { it.run() })
            .setTransactionExecutor(Executor { it.run() })
            .build()
        // 不假设 data 未定义的 DAO 名称；用 Room 标准 API 填入计划给定的表。
        db.openHelper.writableDatabase.execSQL("INSERT OR REPLACE INTO global_state (id, enabled, pausedUntil, ruleVersion) VALUES (0, 1, NULL, 0)")
        logs = OpLogRepository(db.opLogDao(), clock)
        events = EventRepository(db.eventDao(), clock)
        configs = AppConfigRepository(db, clock, GlobalStateRepository(db, clock), SignalRepository(db, clock))
        statuses = EngineStatusRepository(db.engineStatusDao(), clock)
    }

    protected fun seedApp(pkg: String, overlay: Boolean = false, clipboard: Boolean = false) {
        db.openHelper.writableDatabase.execSQL(
            """INSERT OR REPLACE INTO app_config
                (pkg, label, level, splash, rewarded, shake, jumpBack, notify, limitOverlay,
                 denyClipboard, sensitive, tempAllowUntil, firstSeenAt, observationEndsAt)
                VALUES (?, ?, 'STANDARD', 1, 'SILENT', 1, 1, 1, ?, ?, 0, NULL, ?, 0)""",
            arrayOf<Any>(pkg, pkg, if (overlay) 1 else 0, if (clipboard) 1 else 0, clock.now()),
        )
    }

    protected fun appSync(shell: Shell, executor: OpExecutor, profile: ColorOsProfile?, state: MutableStateFlow<ShizukuState>) =
        AppOpsSync(shell, executor, profile, configs, logs, state)

    @After fun closeMemoryData() {
        try { if (::db.isInitialized) db.close() } finally { Dispatchers.resetMain() }
    }
}
