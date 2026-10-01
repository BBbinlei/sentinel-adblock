package com.sentinel.a11y.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sentinel.rules.model.UiRule
import com.sentinel.rules.parse.JsonRuleParser

object A11yNotifications {
    const val CHANNEL_ID = "a11y"

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "界面拦截提醒", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun label(context: Context, pkg: String) = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** 学习模式：刚才手动关闭的弹窗，以后自动关？ */
    fun askLearn(context: Context, rule: UiRule, pkg: String) {
        fun action(action: String, text: String): Notification.Action {
            val intent = Intent(context, A11yActionReceiver::class.java).setAction(action)
                .setData(Uri.Builder().scheme("sentinel").authority("a11y-learn")
                    .appendPath(action).appendQueryParameter("rule", rule.id).build())
                .putExtra(A11yActions.EXTRA_RULE_JSON, JsonRuleParser.encode(listOf(rule)))
            val pending = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return Notification.Action.Builder(null, text, pending).build()
        }
        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("刚才关闭的弹窗，以后自动关？")
            .setContentText("${label(context, pkg)} 的这个页面")
            .addAction(action(A11yActions.LEARN_ACCEPT, "是"))
            .addAction(action(A11yActions.LEARN_REJECT, "否"))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(rule.id, 0, n)
    }

    fun cancelLearn(context: Context, ruleId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(ruleId, 0)
    }

    /** 默认勾选了自动续费 / 连续包月。 */
    fun warnAutoRenew(context: Context, pkg: String) {
        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("发现默认勾选了自动续费")
            .setContentText("${label(context, pkg)} 的页面里勾选了自动续费 / 连续包月，请确认是否需要")
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify("autorenew:$pkg", 0, n)
    }
}
