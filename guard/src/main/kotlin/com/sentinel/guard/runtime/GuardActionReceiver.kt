package com.sentinel.guard.runtime

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext

class GuardActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GuardNotifier.ACTION_UNDO) return
        val pkg = intent.getStringExtra(GuardNotifier.EXTRA_PKG)
        val ruleIds = intent.getStringArrayExtra(GuardNotifier.EXTRA_RULE_IDS)
        if (pkg.isNullOrBlank() || ruleIds == null) return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                GlobalContext.get().get<GuardRunner>().undo(pkg, ruleIds.toList())
                context.getSystemService(NotificationManager::class.java)
                    .cancel(AndroidGuardNotifier.notificationTag(pkg), AndroidGuardNotifier.notificationId("disabled"))
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w("SentinelGuard", "撤销失败", e)
            } finally { pending.finish(); scope.cancel() }
        }
    }
}
