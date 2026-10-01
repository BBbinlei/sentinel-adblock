package com.sentinel.vpn.health

import com.sentinel.data.db.EngineId
import com.sentinel.data.db.EngineState
import com.sentinel.data.repo.EngineStatusRepository

interface EnabledServicesChecker {
    fun accessibilityEnabled(): Boolean
    fun notificationListenerEnabled(): Boolean
    /** engine_status 中曾经为 RUNNING。 */
    fun userWantsA11y(): Boolean
    fun userWantsNotify(): Boolean
}

class ServiceWatchdog(private val checker: EnabledServicesChecker, private val status: EngineStatusRepository,
    private val notifier: (EngineId) -> Unit) {
    suspend fun checkOnce() {
        if (checker.userWantsA11y() && !checker.accessibilityEnabled()) lost(EngineId.A11Y, "无障碍服务已被关闭，请重新开启")
        if (checker.userWantsNotify() && !checker.notificationListenerEnabled()) lost(EngineId.NOTIFY, "通知使用权已被关闭，请重新开启")
    }
    private suspend fun lost(engine: EngineId, message: String) {
        status.report(engine, EngineState.STOPPED, message)
        notifier(engine)
    }
}
