package com.sentinel.guard.di

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.sentinel.data.module.ModuleEntry
import com.sentinel.data.module.ProcessKind
import com.sentinel.guard.runtime.GuardNotifier
import com.sentinel.guard.runtime.GuardRunner
import com.sentinel.guard.runtime.ObservationWorker
import kotlinx.coroutines.CoroutineScope
import org.koin.core.Koin
import java.util.concurrent.atomic.AtomicBoolean

class GuardEntry : ModuleEntry {
    override val id = "guard"
    override val processes = setOf(ProcessKind.MAIN)
    override val koinModule = guardModule
    private val started = AtomicBoolean()

    override fun start(context: Context, scope: CoroutineScope, koin: Koin) {
        if (!started.compareAndSet(false, true)) return
        try {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(GuardNotifier.CHANNEL_ID, "健康守护", NotificationManager.IMPORTANCE_DEFAULT))
        } catch (e: Exception) { Log.w("SentinelGuard", "通知渠道创建失败", e) }
        try { koin.get<GuardRunner>().start(scope) }
        catch (e: Exception) { Log.w("SentinelGuard", "守护启动失败", e) }
        try { ObservationWorker.schedule(context) }
        catch (e: Exception) { Log.w("SentinelGuard", "观察期任务登记失败", e) }
    }
}
