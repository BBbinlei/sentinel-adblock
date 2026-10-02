package com.sentinel.app

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.sentinel.app.di.PREF_ASKED_NOTIFICATIONS
import com.sentinel.app.di.PREF_ONBOARDING_DONE
import com.sentinel.app.ui.nav.Routes
import com.sentinel.app.ui.nav.SentinelNavHost
import com.sentinel.app.ui.theme.SentinelTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val prefs: SharedPreferences by inject()

    // guard 的撤销提醒、学习确认、系统净化提醒都靠通知；Android 13+ 需要运行时授权，不授权也不影响拦截本身
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (prefs.getBoolean(PREF_ASKED_NOTIFICATIONS, false)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        prefs.edit().putBoolean(PREF_ASKED_NOTIFICATIONS, true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionOnce()
        val start = if (prefs.getBoolean(PREF_ONBOARDING_DONE, false)) Routes.HOME else Routes.ONBOARDING
        setContent {
            SentinelTheme {
                SentinelNavHost(navController = rememberNavController(), startDestination = start)
            }
        }
    }
}
