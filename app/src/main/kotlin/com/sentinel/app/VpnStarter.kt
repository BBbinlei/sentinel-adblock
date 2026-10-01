package com.sentinel.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

/**
 * 启动 engine-vpn 的服务。app 不引用引擎的类，只用组件名与动作字符串（与 engine-vpn 的 VpnActions 一致）。
 */
object VpnStarter {
    private const val SERVICE_CLASS = "com.sentinel.vpn.service.SentinelVpnService"
    private const val ACTION_START = "com.sentinel.vpn.START"

    /** 需要用户授权时返回系统授权界面的 Intent，已授权返回 null。 */
    fun intentToPrepare(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        val intent = Intent(ACTION_START).setComponent(ComponentName(context.packageName, SERVICE_CLASS))
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            // 启动失败只会让拦截变弱，不能让 app 崩溃；首页会从引擎状态看到「已停止」
            android.util.Log.w("SentinelApp", "启动 VPN 服务失败", e)
        }
    }
}
