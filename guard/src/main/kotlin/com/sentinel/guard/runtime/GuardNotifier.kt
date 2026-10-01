package com.sentinel.guard.runtime

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

interface GuardNotifier {
    fun notifyDisabled(pkg: String, label: String, ruleIds: List<String>, reason: String)
    fun notifyNoRule(pkg: String, label: String, reason: String)
    fun notifyObservationExtended(pkg: String, label: String)

    companion object {
        const val CHANNEL_ID = "guard"
        const val ACTION_UNDO = "com.sentinel.guard.UNDO"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_RULE_IDS = "ruleIds"
    }
}

class AndroidGuardNotifier(private val context: Context) : GuardNotifier {
    override fun notifyDisabled(pkg: String, label: String, ruleIds: List<String>, reason: String) {
        val undo = Intent(context, GuardActionReceiver::class.java)
            .setAction(GuardNotifier.ACTION_UNDO)
            .setData(Uri.Builder().scheme("sentinel").authority("guard").appendPath("undo").appendPath(pkg).build())
            .putExtra(GuardNotifier.EXTRA_PKG, pkg)
            .putExtra(GuardNotifier.EXTRA_RULE_IDS, ruleIds.toTypedArray())
        val pending = PendingIntent.getBroadcast(context, 0, undo,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        post(pkg, "disabled", "${label}疑似受影响，已自动降级", "原因：$reason；可点「撤销」恢复并钉住这些规则") {
            addAction(0, "撤销", pending)
        }
    }

    override fun notifyNoRule(pkg: String, label: String, reason: String) =
        post(pkg, "norule", "${label}疑似受影响", "原因：$reason；未找到可停用的规则，可在应用详情里临时放行")

    override fun notifyObservationExtended(pkg: String, label: String) =
        post(pkg, "observe", "${label}观察期已延长 3 天", "观察期内出现过撤销或临时放行，暂不开始网络拦截")

    @SuppressLint("MissingPermission")
    private fun post(pkg: String, kind: String, title: String, text: String,
        extra: NotificationCompat.Builder.() -> Unit = {}) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w("SentinelGuard", "无通知权限，跳过通知: $pkg")
            return
        }
        val notification = NotificationCompat.Builder(context, GuardNotifier.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text)
            .setAutoCancel(true)
            .apply(extra).build()
        NotificationManagerCompat.from(context).notify(notificationTag(pkg), notificationId(kind), notification)
    }

    companion object {
        fun notificationTag(pkg: String) = "guard:$pkg"
        fun notificationId(kind: String) = kind.hashCode()
    }
}
