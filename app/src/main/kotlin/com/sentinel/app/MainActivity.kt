package com.sentinel.app

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.sentinel.app.di.PREF_ONBOARDING_DONE
import com.sentinel.app.ui.nav.Routes
import com.sentinel.app.ui.nav.SentinelNavHost
import com.sentinel.app.ui.theme.SentinelTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val prefs: SharedPreferences by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = if (prefs.getBoolean(PREF_ONBOARDING_DONE, false)) Routes.HOME else Routes.ONBOARDING
        setContent {
            SentinelTheme {
                SentinelNavHost(navController = rememberNavController(), startDestination = start)
            }
        }
    }
}
