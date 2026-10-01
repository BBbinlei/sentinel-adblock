package com.sentinel.app.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

enum class SetupStep { SHIZUKU, VPN, ACCESSIBILITY, NOTIFICATION, BATTERY }

interface SetupChecker {
    fun isDone(step: SetupStep): Boolean
    fun settingsIntent(step: SetupStep): Intent
}

/**
 * 真机上的步骤判定。Shizuku 是否就绪由 engine-system 提供，经 [shizukuReady] 注入，
 * 这样 app 的向导不直接引用引擎类。
 */
class AndroidSetupChecker(
    private val context: Context,
    private val shizukuReady: () -> Boolean,
    private val requestShizuku: () -> Unit = {},
) : SetupChecker {
    override fun isDone(step: SetupStep): Boolean = try {
        when (step) {
            SetupStep.SHIZUKU -> shizukuReady()
            SetupStep.VPN -> VpnService.prepare(context) == null
            SetupStep.ACCESSIBILITY -> accessibilityEnabled()
            SetupStep.NOTIFICATION -> context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
            SetupStep.BATTERY -> context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
        }
    } catch (e: Exception) {
        false
    }

    override fun settingsIntent(step: SetupStep): Intent = when (step) {
        SetupStep.SHIZUKU -> {
            requestShizuku()
            context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
        }
        SetupStep.VPN -> VpnService.prepare(context) ?: Intent(Settings.ACTION_VPN_SETTINGS)
        SetupStep.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        SetupStep.NOTIFICATION -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        SetupStep.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it.packageName == context.packageName && it.className == A11Y_SERVICE_CLASS }
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val A11Y_SERVICE_CLASS = "com.sentinel.a11y.service.SentinelAccessibilityService"
    }
}
