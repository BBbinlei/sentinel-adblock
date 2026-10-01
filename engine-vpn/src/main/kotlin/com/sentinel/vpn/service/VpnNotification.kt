package com.sentinel.vpn.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build

object VpnActions {
    const val PAUSE = "com.sentinel.vpn.PAUSE"
    const val START = "com.sentinel.vpn.START"
    const val SERVICE_CLASS = "com.sentinel.vpn.service.SentinelVpnService"
}

object VpnNotification {
    const val CHANNEL_ID = "vpn"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "网络拦截", NotificationManager.IMPORTANCE_LOW))
    }

    fun build(context: Context, todayBlocked: Int, warning: String? = null): Notification {
        ensureChannel(context)
        val pause = PendingIntent.getService(context, 0,
            Intent(context, SentinelVpnService::class.java).setAction(VpnActions.PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("哨兵")
            .setContentText(warning ?: "今日拦截 $todayBlocked")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "暂停 5 分钟", pause).build())
            .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .build()
    }
}
