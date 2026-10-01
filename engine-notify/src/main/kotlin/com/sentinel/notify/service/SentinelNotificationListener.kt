package com.sentinel.notify.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sentinel.data.db.EngineId
import com.sentinel.data.db.EngineState
import com.sentinel.notify.core.NotifyAction
import com.sentinel.notify.core.PostedNotification
import com.sentinel.notify.di.NotifyRuntime
import com.sentinel.rules.model.EventKind
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext

fun StatusBarNotification.toPosted() = PostedNotification(packageName, notification.channelId,
    notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
    notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
    notification.flags and Notification.FLAG_ONGOING_EVENT != 0, key)

class SentinelNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // ponytail: callbacks serialize to preserve lifecycle order; split per-package work if throughput requires it.
    private val callbacks = Mutex()
    private val runtime get() = GlobalContext.get().get<NotifyRuntime>()

    private fun dispatch(stop: Boolean = false, block: suspend () -> Unit) {
        scope.launch {
            try { callbacks.withLock { withContext(Dispatchers.IO) { block() } } }
            catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w("SentinelNotify", "通知回调处理失败", e)
            } finally { if (stop) scope.cancel() }
        }
    }

    override fun onListenerConnected() = dispatch {
        runtime.reloadRules()
        runtime.statuses.report(EngineId.NOTIFY, EngineState.RUNNING)
    }

    override fun onListenerDisconnected() = dispatch {
        runtime.statuses.report(EngineId.NOTIFY, EngineState.STOPPED)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = dispatch {
        val posted = sbn.toPosted()
        val action = runtime.engineFor(posted.pkg).onPosted(posted)
        if (action is NotifyAction.Cancel) {
            cancelNotification(action.key)
            runtime.events.log(action.pkg, EventKind.NOTIFICATION_CANCELLED, action.ruleId, action.title)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (reason != REASON_CANCEL) return
        dispatch {
            val posted = sbn.toPosted()
            if (posted.ongoing) return@dispatch
            val action = runtime.engineFor(posted.pkg).onUserDismissed(posted.pkg, posted.channelId)
            if (action is NotifyAction.AskLearn) NotifyLearnReceiver.ask(this, action.rule)
        }
    }

    override fun onDestroy() {
        dispatch(stop = true) { runtime.statuses.report(EngineId.NOTIFY, EngineState.STOPPED) }
        super.onDestroy()
    }
}
