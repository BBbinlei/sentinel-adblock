package com.sentinel.app

import android.app.Application
import android.util.Log
import com.sentinel.app.di.ModuleLoader
import com.sentinel.app.di.appModule
import com.sentinel.data.di.dataModule
import com.sentinel.data.module.ProcessKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SentinelApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val process = if (getProcessName().endsWith(":vpn")) ProcessKind.VPN else ProcessKind.MAIN
        val entries = ModuleLoader.load(process, Thread.currentThread().contextClassLoader)
        val koin = startKoin {
            androidContext(this@SentinelApp)
            modules(listOf(dataModule, appModule) + entries.map { it.koinModule })
        }.koin
        if (process == ProcessKind.MAIN && getSharedPreferences("launch", MODE_PRIVATE)
                .getBoolean("popupEnabled", false)) appScope.launch {
            try { koin.get<com.sentinel.app.launch.PopupRuleControl>().removeUnsupported(this@SentinelApp) }
            catch (e: Exception) { Log.w("SentinelApp", "首页弹窗规则版本检查失败", e) }
        }
        if (process == ProcessKind.MAIN && getSharedPreferences("launch", MODE_PRIVATE)
                .getBoolean("originalIconEnabled", false)) {
            com.sentinel.app.launch.OriginalIconControl.get(this)
        }
        // 每个模块自己创建通知渠道、启动协程、注册周期任务；单个入口失败不影响其他入口与界面
        entries.forEach { entry ->
            try {
                entry.start(this, appScope, koin)
            } catch (e: Throwable) {
                Log.e("SentinelApp", "模块 ${entry.id} 启动失败", e)
            }
        }
    }
}
