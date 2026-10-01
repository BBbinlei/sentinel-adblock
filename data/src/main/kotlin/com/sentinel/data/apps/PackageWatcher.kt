package com.sentinel.data.apps

import android.content.*
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

class PackageWatcher(context: Context, private val registry: AppRegistry, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private var started = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            val pending = goAsync()
            scope.launch(Dispatchers.IO) {
                try {
                    when (intent.action) {
                        Intent.ACTION_PACKAGE_ADDED -> {
                            val info = context.packageManager.getApplicationInfo(pkg, 0)
                            registry.onPackageAdded(InstalledApp(pkg, context.packageManager.getApplicationLabel(info).toString()))
                        }
                        Intent.ACTION_PACKAGE_REMOVED -> registry.onPackageRemoved(pkg)
                    }
                } catch (e: Exception) { Log.w("SentinelData", "App 登记失败", e) }
                finally { pending.finish() }
            }
        }
    }
    fun start() {
        if (started) return
        val filter = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addDataScheme("package") }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(receiver, filter)
        started = true
    }
    fun stop() { if (started) { context.unregisterReceiver(receiver); started = false } }
}
