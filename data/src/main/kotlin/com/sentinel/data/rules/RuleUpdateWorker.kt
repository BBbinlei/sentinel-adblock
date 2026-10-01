package com.sentinel.data.rules

import android.content.Context
import android.util.Log
import androidx.work.*
import com.sentinel.data.contract.DataContract
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class RuleUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val report = GlobalContext.get().get<SubscriptionUpdater>().updateAll()
        if (report.failed == 0) Result.success() else Result.retry()
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Log.w("SentinelData", "规则更新失败，继续使用旧规则", e)
        Result.retry()
    }
    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RuleUpdateWorker>(DataContract.RULE_UPDATE_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DataContract.RULE_UPDATE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
