package com.sentinel.system.drift

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class DriftWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val report = GlobalContext.get().get<DriftInspector>().inspect()
        if (report != null && report.drifted.isNotEmpty()) notify(report)
        Result.success()
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Log.w("SentinelSystem", "巡检失败", e)
        Result.success()
    }

    private fun notify(report: DriftReport) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "系统设置巡检", NotificationManager.IMPORTANCE_DEFAULT))
        val reapply = Intent(ACTION_REAPPLY).setPackage(context.packageName)
            .putStringArrayListExtra(EXTRA_IDS, ArrayList(report.drifted))
        val pending = PendingIntent.getBroadcast(context, 0, reapply, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("${report.drifted.size} 项设置被系统恢复")
            .addAction(0, "一键重新执行", pending).setAutoCancel(true).build()
        manager.notify(NOTIFICATION_ID, n)
    }

    companion object {
        const val WORK_NAME = "system-drift"
        const val CHANNEL = "system"
        const val ACTION_REAPPLY = "com.sentinel.system.REAPPLY"
        const val EXTRA_IDS = "opIds"
        const val NOTIFICATION_ID = 7101
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DriftWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
