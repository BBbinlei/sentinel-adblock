package com.sentinel.notify.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.sentinel.notify.di.NotifyRuntime
import com.sentinel.rules.model.NotifyRule
import com.sentinel.rules.parse.JsonRuleParser
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext

class NotifyLearnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LEARN_ACCEPT && intent.action != LEARN_REJECT) return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val rule = JsonRuleParser.parse(requireNotNull(intent.getStringExtra(RULE_JSON))).single() as NotifyRule
                require(!rule.pkg.isNullOrBlank() && rule.pkg != context.packageName &&
                    !rule.channelId.isNullOrBlank() && rule.keywords.isEmpty() &&
                    rule.id == "learn:notify:${rule.pkg}:${rule.channelId}") { "无效的通知学习规则" }
                val runtime = GlobalContext.get().get<NotifyRuntime>()
                if (intent.action == LEARN_ACCEPT) runtime.accept(rule)
                else {
                    val pkg = requireNotNull(rule.pkg)
                    runtime.engineFor(pkg).onLearnRejected(pkg, rule.channelId)
                }
                context.getSystemService(NotificationManager::class.java).cancel(rule.id, 0)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w("SentinelNotify", "通知学习操作失败", e)
            } finally { pending.finish(); scope.cancel() }
        }
    }

    companion object {
        const val CHANNEL_ID = "notify-learn"
        const val LEARN_ACCEPT = "com.sentinel.notify.LEARN_ACCEPT"
        const val LEARN_REJECT = "com.sentinel.notify.LEARN_REJECT"
        const val RULE_JSON = "ruleJson"

        fun ask(context: Context, rule: NotifyRule) {
            fun action(action: String, label: String): Notification.Action {
                val intent = Intent(context, NotifyLearnReceiver::class.java).setAction(action)
                    .setData(Uri.Builder().scheme("sentinel").authority("notify-learn")
                        .appendPath(action).appendQueryParameter("rule", rule.id).build())
                    .putExtra(RULE_JSON, JsonRuleParser.encode(listOf(rule)))
                val pending = PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                return Notification.Action.Builder(null, label, pending).build()
            }
            val pkg = requireNotNull(rule.pkg)
            val label = runCatching {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("以后自动过滤？")
                .setContentText("你多次划掉了${label}的同类通知")
                .addAction(action(LEARN_ACCEPT, "是"))
                .addAction(action(LEARN_REJECT, "否"))
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(rule.id, 0, notification)
        }
    }
}
