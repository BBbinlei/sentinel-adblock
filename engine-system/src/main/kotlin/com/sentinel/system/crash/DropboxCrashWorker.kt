package com.sentinel.system.crash

import android.content.Context
import android.util.Log
import androidx.work.*
import com.sentinel.data.db.ProtectLevel
import com.sentinel.data.db.SignalKind
import com.sentinel.data.repo.AppConfigRepository
import com.sentinel.data.repo.SignalRepository
import com.sentinel.system.shell.Shell
import com.sentinel.system.shell.ShizukuGateway
import com.sentinel.system.shell.ShizukuState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class DropboxCrashWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val koin = GlobalContext.get()
        val gateway = koin.get<ShizukuGateway>()
        if (gateway.state() != ShizukuState.READY) Result.success()
        else {
            collect(gateway, koin.get(), koin.get(), System.currentTimeMillis())
            Result.success()
        }
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Log.w("SentinelSystem", "崩溃日志采集失败", e)
        Result.success()
    }

    private suspend fun collect(shell: Shell, signals: SignalRepository, configs: AppConfigRepository, now: Long) {
        val prefs = context.getSharedPreferences("sentinel-system", Context.MODE_PRIVATE)
        val last = prefs.getLong(LAST_CHECKED, now)
        val result = shell.exec("dumpsys dropbox --print data_app_crash")
        if (!result.ok) return
        for (e in DropboxParser.parse(result.stdout)) {
            if (e.ts <= last) continue
            if (configs.effective(e.pkg).level == ProtectLevel.OFF) continue
            signals.emit(e.pkg, SignalKind.DROPBOX_CRASH)
        }
        prefs.edit().putLong(LAST_CHECKED, now).apply()
    }

    companion object {
        const val WORK_NAME = "system-dropbox"
        private const val LAST_CHECKED = "dropbox-last-checked"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DropboxCrashWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
