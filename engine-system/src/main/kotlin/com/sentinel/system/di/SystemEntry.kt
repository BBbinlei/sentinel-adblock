package com.sentinel.system.di

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.sentinel.data.module.ModuleEntry
import com.sentinel.data.module.ProcessKind
import com.sentinel.system.crash.DropboxCrashWorker
import com.sentinel.system.drift.DriftWorker
import com.sentinel.system.drift.SystemStatusReporter
import com.sentinel.system.ops.AppOpsSync
import kotlinx.coroutines.CoroutineScope
import org.koin.core.Koin
import org.koin.core.module.Module
import java.util.concurrent.atomic.AtomicBoolean

class SystemEntry : ModuleEntry {
    override val id = "system"
    override val processes = setOf(ProcessKind.MAIN)
    override val koinModule: Module = systemModule
    private val started = AtomicBoolean()

    override fun start(context: Context, scope: CoroutineScope, koin: Koin) {
        if (!started.compareAndSet(false, true)) return
        try {
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(NotificationChannel(DriftWorker.CHANNEL, "系统设置巡检", NotificationManager.IMPORTANCE_DEFAULT))
        } catch (e: Exception) { Log.w("SentinelSystem", "通知渠道创建失败", e) }
        try { koin.get<AppOpsSync>().start(scope) } catch (e: Exception) { Log.w("SentinelSystem", "权限同步启动失败", e) }
        try { koin.get<SystemStatusReporter>().start(scope) } catch (e: Exception) { Log.w("SentinelSystem", "状态上报启动失败", e) }
        try { DriftWorker.schedule(context) } catch (e: Exception) { Log.w("SentinelSystem", "巡检任务登记失败", e) }
        try { DropboxCrashWorker.schedule(context) } catch (e: Exception) { Log.w("SentinelSystem", "崩溃采集任务登记失败", e) }
    }
}
