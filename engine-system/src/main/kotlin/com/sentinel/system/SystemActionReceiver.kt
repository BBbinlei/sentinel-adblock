package com.sentinel.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sentinel.system.drift.DriftInspector
import com.sentinel.system.drift.DriftWorker
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext

class SystemActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DriftWorker.ACTION_REAPPLY) return
        val ids = intent.getStringArrayListExtra(DriftWorker.EXTRA_IDS).orEmpty()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { GlobalContext.get().get<DriftInspector>().reapply(ids) }
            catch (e: Exception) { Log.w("SentinelSystem", "一键重新执行失败", e) }
            finally { pending.finish() }
        }
    }
}
