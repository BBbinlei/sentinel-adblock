package com.sentinel.guard.runtime

import android.util.Log
import com.sentinel.data.db.SignalEntity
import com.sentinel.data.repo.*
import com.sentinel.guard.policy.Decision
import com.sentinel.guard.policy.GuardPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GuardRunner(
    private val signals: SignalRepository,
    private val events: EventRepository,
    private val overrides: OverrideRepository,
    private val apps: AppConfigRepository,
    private val notifier: GuardNotifier,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            try {
                signals.observeUnhandled().collect { batch ->
                    for (signal in batch) handle(signal)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SentinelGuard", "信号订阅中断", e)
            }
        }
    }

    /** undo = remove the automatic degradation, then pin so guard never disables the rule again. */
    suspend fun undo(pkg: String, ruleIds: List<String>) {
        for (id in ruleIds.distinct()) {
            overrides.remove(pkg, id)
            overrides.pin(pkg, id)
        }
    }

    private suspend fun handle(signal: SignalEntity) {
        try {
            val recentHits = events.rulesHitSince(signal.pkg, signal.ts - GuardPolicy.hitWindowMs(signal.kind))
            val count24h = signals.countSince(signal.pkg, setOf(signal.kind), signal.ts - DAY_MS)
            val decision = GuardPolicy.onSignal(signal, recentHits, count24h)
            if (decision != null) execute(decision)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SentinelGuard", "信号处理失败: ${signal.id}", e)
        }
        try {
            signals.markHandled(listOf(signal.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SentinelGuard", "标记信号已处理失败: ${signal.id}", e)
        }
    }

    private suspend fun execute(decision: Decision) {
        when (decision) {
            is Decision.DisableRules -> {
                val disabled = decision.ruleIds.filter { overrides.disable(decision.pkg, it, "自动降级：${decision.reason}") }
                if (disabled.isEmpty()) notifier.notifyNoRule(decision.pkg, labelOf(decision.pkg), decision.reason)
                else notifier.notifyDisabled(decision.pkg, labelOf(decision.pkg), disabled, decision.reason)
            }
            is Decision.NoRuleFound -> notifier.notifyNoRule(decision.pkg, labelOf(decision.pkg), decision.reason)
            // Observation decisions come only from ObservationWorker, never from signals.
            is Decision.ExtendObservation, is Decision.EndObservation -> Unit
        }
    }

    private suspend fun labelOf(pkg: String): String = apps.observe(pkg).first()?.label ?: pkg

    private companion object { const val DAY_MS = 86_400_000L }
}
