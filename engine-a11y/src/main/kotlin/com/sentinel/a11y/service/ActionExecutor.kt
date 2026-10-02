package com.sentinel.a11y.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.sentinel.a11y.core.A11yAction
import com.sentinel.a11y.di.A11yRuntime
import com.sentinel.data.db.RewardedMode
import com.sentinel.rules.model.EventKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 执行 A11yBrain 返回的动作。任何异常都被捕获并记日志，不允许抛出。 */
class ActionExecutor(
    private val service: AccessibilityService,
    private val runtime: A11yRuntime,
    private val overlay: OverlayToast,
    private val scope: CoroutineScope,
    private val onRewardedChoice: (Boolean) -> Unit,
    private val onAutoClick: () -> Unit,
) {
    suspend fun execute(actions: List<A11yAction>) {
        for (a in actions) {
            try { run(a) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w(A11yRuntime.TAG, "动作执行失败: $a", e) }
        }
    }

    private suspend fun run(a: A11yAction) {
        when (a) {
            is A11yAction.Click -> {
                onAutoClick()
                clickNode(a)
                runtime.events.log(a.pkg, a.kind, a.ruleId)
            }
            is A11yAction.Revert -> revert(a)
            is A11yAction.OpenRewardWindow -> runtime.rewardWindows.open(a.pkg)
            is A11yAction.AskRewarded -> overlay.showRewardedAsk { silent, remember ->
                onRewardedChoice(silent)
                if (remember && silent) scope.launch {
                    try { runtime.appConfigs.setRewarded(a.pkg, RewardedMode.SILENT) }
                    catch (e: Exception) { Log.w(A11yRuntime.TAG, "保存激励模式失败", e) }
                }
            }
            is A11yAction.SetMuted -> Unit // 音量已由 VolumeKeeper 处理。
            is A11yAction.WarnAutoRenew -> {
                A11yNotifications.warnAutoRenew(service, a.pkg)
                runtime.events.log(a.pkg, EventKind.AUTO_RENEW_WARNED, null)
            }
            is A11yAction.ProposeRule -> {
                val s = a.rule.scope
                val pkg = when (s) {
                    is com.sentinel.rules.model.RuleScope.Page -> s.pkg
                    is com.sentinel.rules.model.RuleScope.App -> s.pkg
                    com.sentinel.rules.model.RuleScope.Global -> return
                }
                A11yNotifications.askLearn(service, a.rule, pkg)
            }
            is A11yAction.Signal -> runtime.signals.emit(a.signal.pkg, a.signal.kind, null, a.signal.detail)
        }
    }

    private fun clickNode(a: A11yAction.Click) {
        val node = a.node as? NodeViewAdapter ?: return
        var cur: AccessibilityNodeInfo? = node.info
        var depth = 0
        while (cur != null && depth < MAX_ANCESTORS) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            cur = cur.parent
            depth++
        }
    }

    private suspend fun revert(a: A11yAction.Revert) {
        val j = a.jump
        try {
            val intent = service.packageManager.getLaunchIntentForPackage(j.source)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent == null) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            else service.startActivity(intent)
        } catch (e: Exception) {
            Log.w(A11yRuntime.TAG, "后台无法启动源 App，改用返回键", e)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        runtime.events.log(j.source, EventKind.JUMP_REVERTED, com.sentinel.a11y.core.JumpBackGuard.RULE_ID, "${j.source}->${j.target}:${j.reason}")
        overlay.showUndo("已拦截 ${label(j.source)} → ${label(j.target)} 的跳转", TOAST_MS) {
            scope.launch {
                try { UndoJump.run(runtime, j.source, j.target) }
                catch (e: Exception) { Log.w(A11yRuntime.TAG, "撤销失败", e) }
            }
        }
    }

    private fun label(pkg: String) = runCatching {
        service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        const val TOAST_MS = 3_000L
        private const val MAX_ANCESTORS = 6
    }
}
