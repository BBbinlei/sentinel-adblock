package com.sentinel.notify.di

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.sentinel.data.module.ModuleEntry
import com.sentinel.data.module.ProcessKind
import com.sentinel.data.repo.GlobalStateRepository
import com.sentinel.notify.service.NotifyLearnReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.Koin

class NotifyEntry : ModuleEntry {
    override val id = "notify"
    override val processes = setOf(ProcessKind.MAIN)
    override val koinModule = notifyModule

    override fun start(context: Context, scope: CoroutineScope, koin: Koin) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(NotifyLearnReceiver.CHANNEL_ID, "通知过滤学习", NotificationManager.IMPORTANCE_DEFAULT))
        scope.launch(Dispatchers.IO) {
            try {
                koin.get<GlobalStateRepository>().observe().map { it.ruleVersion }.distinctUntilChanged().collect {
                    try { koin.get<NotifyRuntime>().reloadRules() }
                    catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        Log.w("SentinelNotify", "通知规则加载失败", e)
                    }
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w("SentinelNotify", "通知规则订阅失败", e)
            }
        }
    }
}
