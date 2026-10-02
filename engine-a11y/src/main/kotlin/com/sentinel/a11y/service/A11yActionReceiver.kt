package com.sentinel.a11y.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sentinel.a11y.di.A11yRuntime
import com.sentinel.rules.model.RuleScope
import com.sentinel.rules.model.UiRule
import com.sentinel.rules.parse.JsonRuleParser
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext

class A11yActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != A11yActions.LEARN_ACCEPT && action != A11yActions.LEARN_REJECT && action != A11yActions.JUMP_UNDO) return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val runtime = GlobalContext.get().get<A11yRuntime>()
                when (action) {
                    A11yActions.JUMP_UNDO -> UndoJump.run(runtime,
                        requireNotNull(intent.getStringExtra(A11yActions.EXTRA_SOURCE)),
                        requireNotNull(intent.getStringExtra(A11yActions.EXTRA_TARGET)))
                    else -> {
                        val rule = JsonRuleParser.parse(requireNotNull(intent.getStringExtra(A11yActions.EXTRA_RULE_JSON))).single() as UiRule
                        val scopePkg = when (val s = rule.scope) {
                            is RuleScope.Page -> s.pkg
                            is RuleScope.App -> s.pkg
                            RuleScope.Global -> null
                        }
                        require(scopePkg != null && scopePkg != context.packageName &&
                            rule.id.startsWith("learn:$scopePkg:") && rule.source == "learned") { "无效的界面学习规则" }
                        if (action == A11yActions.LEARN_ACCEPT) runtime.acceptLearned(rule)
                        A11yNotifications.cancelLearn(context, rule.id)
                    }
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w(A11yRuntime.TAG, "通知动作处理失败", e)
            } finally { pending.finish(); scope.cancel() }
        }
    }
}

/** 撤销跳转回退：打开目标 App、记住例外、上报 USER_UNDO。 */
object UndoJump {
    suspend fun run(runtime: A11yRuntime, source: String, target: String) {
        val context = runtime.context
        try { runtime.addException(source, target) }
        catch (e: Exception) { Log.w(A11yRuntime.TAG, "撤销例外保存失败，保留本进程临时例外", e) }
        try { runtime.signals.emit(source, com.sentinel.data.db.SignalKind.USER_UNDO) }
        catch (e: Exception) { Log.w(A11yRuntime.TAG, "撤销信号上报失败", e) }
        try {
            context.packageManager.getLaunchIntentForPackage(target)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
        } catch (e: Exception) {
            Log.w(A11yRuntime.TAG, "撤销时无法打开目标 App", e)
        }
    }
}
