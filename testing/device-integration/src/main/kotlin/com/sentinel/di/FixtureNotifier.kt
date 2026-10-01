package com.sentinel.di

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 以指定渠道发送通知的夹具（DI-07）。
 *
 * - 渠道不存在时自动创建（IMPORTANCE_DEFAULT）。
 * - Android 13+ 需要 POST_NOTIFICATIONS 运行时权限：未授予时不发送并返回 false，
 *   测试可先执行 `pm grant com.sentinel.di android.permission.POST_NOTIFICATIONS`
 *   （或用 GrantPermissionRule）再调用。
 */
object FixtureNotifier {

    /**
     * 发送一条通知。
     *
     * @return 实际已提交给系统返回 true；因权限或通知被关闭而未发送返回 false。
     */
    @JvmStatic
    @JvmOverloads
    fun post(
        context: Context,
        channelId: String,
        title: String,
        text: String,
        notificationId: Int = DEFAULT_ID,
        channelName: CharSequence = channelId,
    ): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        ensureChannel(nm, channelId, channelName)
        if (!nm.areNotificationsEnabled()) return false

        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(notificationId, notification)
        return true
    }

    /** 取消本夹具发出的通知（测试清理用）。 */
    @JvmStatic
    @JvmOverloads
    fun cancel(context: Context, notificationId: Int = DEFAULT_ID) {
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
    }

    private fun ensureChannel(nm: NotificationManager, channelId: String, name: CharSequence) {
        if (nm.getNotificationChannel(channelId) != null) return
        nm.createNotificationChannel(
            NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    const val DEFAULT_ID: Int = 0x5E17
}
