package com.sentinel.regression

import androidx.room.Room
import com.sentinel.data.Clock
import com.sentinel.data.db.SentinelDatabase
import com.sentinel.data.repo.EventRepository
import com.sentinel.data.repo.OpLogRepository
import com.sentinel.rules.model.EventKind
import com.sentinel.system.ops.OpExecutor
import com.sentinel.system.ops.OpOutcome
import com.sentinel.system.profile.ColorOsProfile
import com.sentinel.system.profile.OpKind
import com.sentinel.system.profile.ProfileLoader
import com.sentinel.system.shell.ExecResult
import com.sentinel.system.shell.Shell
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executor
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class ProfileHealthTest {
    private fun profiles(): Map<File, ColorOsProfile> {
        val directory = listOf(File("engine-system/src/main/assets/profiles"),
            File("../../engine-system/src/main/assets/profiles"))
            .firstOrNull { it.isDirectory } ?: fail("Cannot find production profiles from ${File(".").absolutePath}")
        val files = requireNotNull(directory.listFiles()).filter { it.extension == "json" }.sortedBy { it.name }
        assertTrue(files.isNotEmpty(), "No production profile JSON files in $directory")
        return files.associateWith { ProfileLoader.parse(it.readText()) }
    }

    // RR-06a～e：当前档案全为 WIZARD，将结构检查合并，避免无命令项时出现空测试。
    @Test fun RR_06a_to_e_production_profiles_parse_and_pass_all_health_checks() {
        for ((file, profile) in profiles()) {
            assertTrue(profile.ops.isNotEmpty(), file.name)
            val ids = profile.ops.map { it.id }
            assertEquals(ids.size, ids.toSet().size, "Duplicate op id in ${file.name}")
            assertTrue(profile.ops.map { it.trick }.toSet().containsAll(setOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 21)), file.name)
            for (op in profile.ops) {
                val context = "${file.name}: ${op.id}"
                if (op.kind == OpKind.WIZARD) {
                    assertNotNull(op.intent, context)
                } else {
                    assertFalse(op.apply.isNullOrBlank(), context)
                    assertFalse(op.revert.isNullOrBlank(), context)
                    assertFalse(op.probe.isNullOrBlank(), context)
                    Regex(assertNotNull(op.appliedRegex, context))
                }
                assertFalse(op.verified && op.kind == OpKind.UNINSTALL && !op.optional, context)
            }
        }
    }

    @Test fun RR_06f_all_current_unverified_ops_return_not_verified_without_shell_or_logs() = runTest {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SentinelDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Executor { it.run() })
            .setTransactionExecutor(Executor { it.run() })
            .build()
        try {
            val clock = Clock { 1_700_000_000_000L }
            val logs = OpLogRepository(db.opLogDao(), clock)
            val events = EventRepository(db.eventDao(), clock)
            val commands = mutableListOf<String>()
            val shell = object : Shell {
                override suspend fun exec(cmd: String): ExecResult {
                    commands += cmd
                    return ExecResult(0, "", "")
                }
            }
            val executor = OpExecutor(shell, logs, events, clock)
            for ((file, profile) in profiles()) {
                assertTrue(profile.ops.isNotEmpty(), file.name)
                assertEquals(emptySet(), profile.verifiedAppOps, "Unverified production AppOps in ${file.name}")
                profile.ops.forEach { assertFalse(it.verified, "${file.name}: ${it.id}") }
                assertEquals(profile.ops.associate { it.id to OpOutcome.NotVerified },
                    executor.applyAll(profile.ops), file.name)
                assertEquals(emptyList(), commands, file.name)
                assertEquals(emptyList(), logs.observeAll().first(), file.name)
                assertEquals(emptyList(), events.observeRecent(setOf(EventKind.SYSTEM_OP), 100).first(), file.name)
            }
        } finally {
            db.close()
        }
    }
}
