package com.sentinel.data

import android.content.Context
import androidx.work.*
import androidx.work.testing.*
import com.sentinel.data.contract.DataContract
import com.sentinel.data.db.SentinelDatabase
import com.sentinel.data.di.dataModule
import com.sentinel.data.module.*
import com.sentinel.data.repo.*
import com.sentinel.data.rules.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.test.verify.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.ServiceLoader
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, org.koin.core.annotation.KoinInternalApi::class)
class DataModuleTest {
    @Test fun `UT-DA-6-01 Koin definitions verify and resolve`() {
        dataModule.verify(extraTypes = listOf(Context::class, File::class, okhttp3.OkHttpClient.Builder::class))
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DataContract.DATABASE_NAME)
        val app = koinApplication { androidContext(context); modules(dataModule) }
        try {
            app.koin.get<GlobalStateRepository>(); app.koin.get<AppConfigRepository>(); app.koin.get<EventRepository>()
            app.koin.get<SignalRepository>(); app.koin.get<OverrideRepository>(); app.koin.get<OpLogRepository>()
            app.koin.get<JumpExceptionRepository>(); app.koin.get<RewardWindowRepository>(); app.koin.get<EngineStatusRepository>()
            app.koin.get<UserRuleRepository>(); app.koin.get<RuleStore>(); app.koin.get<SubscriptionUpdater>()
            app.koin.get<com.sentinel.data.apps.AppRegistry>()
        } finally { app.koin.get<SentinelDatabase>().close(); app.close(); context.deleteDatabase(DataContract.DATABASE_NAME) }
    }
    @Test fun `UT-DA-6-02 entry discovery main process and unique periodic work`() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DataContract.DATABASE_NAME)
        File(context.filesDir, "rules").deleteRecursively(); File(context.filesDir, "subs").deleteRecursively()
        val configuration = Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor())
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(context: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    object : Worker(context, workerParameters) { override fun doWork(): Result = Result.success() }
            }).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        val app = koinApplication { androidContext(context); modules(dataModule) }
        val entry = ServiceLoader.load(ModuleEntry::class.java).toList().single { it.id == "data" }
        try {
            assertEquals("data", entry.id); assertEquals(setOf(ProcessKind.MAIN), entry.processes)
            assertTrue(entry.koinModule.mappings.isEmpty())
            entry.start(context, backgroundScope, app.koin)
            entry.start(context, backgroundScope, app.koin)
            val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(DataContract.RULE_UPDATE_WORK).get()
            assertEquals(1, work.size)
            assertEquals(86_400_000L, work.single().periodicityInfo!!.repeatIntervalMillis)
            assertEquals(NetworkType.CONNECTED, work.single().constraints.requiredNetworkType)
        } finally { app.koin.get<SentinelDatabase>().close(); app.close(); context.deleteDatabase(DataContract.DATABASE_NAME) }
    }
}
