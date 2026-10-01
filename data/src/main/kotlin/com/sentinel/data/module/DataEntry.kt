package com.sentinel.data.module

import android.content.Context
import android.util.Log
import com.sentinel.data.apps.*
import com.sentinel.data.rules.*
import kotlinx.coroutines.*
import org.koin.core.Koin
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicBoolean

class DataEntry : ModuleEntry {
    override val id = "data"
    override val processes = setOf(ProcessKind.MAIN)
    override val koinModule = module { }
    private val started = AtomicBoolean()
    override fun start(context: Context, scope: CoroutineScope, koin: Koin) {
        if (!started.compareAndSet(false, true)) return
        try { RuleUpdateWorker.schedule(context) }
        catch (e: Exception) { Log.w("SentinelData", "周期更新登记失败", e) }
        scope.launch(Dispatchers.IO) {
            try {
                if (koin.get<RuleStore>().loadDomainMatcher() == null) koin.get<SubscriptionUpdater>().rebuildFromCache()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w("SentinelData", "离线规则初始化失败", e)
            }
            try {
                val registry = koin.get<AppRegistry>()
                val prefs = context.getSharedPreferences("sentinel-data", Context.MODE_PRIVATE)
                val apps = context.packageManager.getInstalledApplications(0).map {
                    InstalledApp(it.packageName, context.packageManager.getApplicationLabel(it).toString())
                }
                registry.syncInstalled(apps, initial = !prefs.getBoolean("apps-registered", false))
                prefs.edit().putBoolean("apps-registered", true).apply()
                val watcher = PackageWatcher(context, registry, scope)
                watcher.start()
                scope.coroutineContext[Job]?.invokeOnCompletion { runCatching { watcher.stop() }.onFailure { Log.w("SentinelData", "App 监听停止失败", it) } }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w("SentinelData", "App 登记初始化失败", e)
            }
        }
    }
}
