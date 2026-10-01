package com.sentinel.guard.runtime

import android.content.Context
import android.util.Log
import androidx.work.*
import com.sentinel.data.db.SignalKind
import com.sentinel.data.repo.AppConfigRepository
import com.sentinel.data.repo.SignalRepository
import com.sentinel.guard.policy.Decision
import com.sentinel.guard.policy.GuardPolicy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class ObservationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val koin = GlobalContext.get()
        val apps = koin.get<AppConfigRepository>()
        val signals = koin.get<SignalRepository>()
        val notifier = koin.get<GuardNotifier>()
        for (cfg in apps.observationDue()) {
            try {
                val count = signals.countSince(cfg.pkg, COUNTED, cfg.firstSeenAt)
                when (GuardPolicy.evaluateObservation(cfg, count)) {
                    is Decision.ExtendObservation -> {
                        apps.extendObservation(cfg.pkg, EXTEND_MS)
                        notifier.notifyObservationExtended(cfg.pkg, cfg.label)
                    }
                    is Decision.EndObservation -> apps.endObservation(cfg.pkg)
                    else -> Unit
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w("SentinelGuard", "观察期评估失败: ${cfg.pkg}", e)
            }
        }
        Result.success()
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Log.w("SentinelGuard", "观察期检查失败", e)
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "observation-check"
        const val EXTEND_MS = 259_200_000L
        private val COUNTED = setOf(SignalKind.USER_UNDO, SignalKind.TEMP_ALLOW)

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ObservationWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
