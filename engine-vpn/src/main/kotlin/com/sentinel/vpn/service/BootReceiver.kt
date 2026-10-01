package com.sentinel.vpn.service

import android.content.*
import android.net.VpnService
import android.util.Log
import com.sentinel.data.repo.GlobalStateRepository
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val koin = GlobalContext.getOrNull() ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (koin.get<GlobalStateRepository>().get().enabled && VpnService.prepare(context) == null)
                    context.startForegroundService(Intent(context, SentinelVpnService::class.java).setAction(VpnActions.START))
            } catch (e: Exception) { Log.w("SentinelVpn", "开机启动失败", e) }
            finally { pending.finish() }
        }
    }
}
