package com.sentinel.app.fakes

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.sentinel.app.apps.*
import com.sentinel.app.home.*
import com.sentinel.app.onboarding.*
import com.sentinel.app.rules.RulesViewModel
import com.sentinel.app.system.SystemCleanupViewModel
import com.sentinel.app.ui.nav.SentinelNavHost
import com.sentinel.app.ui.theme.SentinelTheme
import com.sentinel.data.Clock
import com.sentinel.data.db.*
import com.sentinel.data.repo.*
import com.sentinel.data.rules.UpdateReport
import com.sentinel.guard.runtime.*
import com.sentinel.system.ops.OpExecutor
import com.sentinel.system.profile.ColorOsProfile
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.robolectric.RuntimeEnvironment

abstract class ComposeAppTest : AppTest() {
    @get:Rule val compose = createComposeRule()
    val system by lazy { FakeSystem(data) }
    val setup = FakeSetupChecker()
    lateinit var navigation: NavHostController
    val guard by lazy { GuardRunner(data.signals, data.events, data.overrides, data.apps,
        object : GuardNotifier {
            override fun notifyDisabled(pkg: String, label: String, ruleIds: List<String>, reason: String) = Unit
            override fun notifyNoRule(pkg: String, label: String, reason: String) = Unit
            override fun notifyObservationExtended(pkg: String, label: String) = Unit
        }, data.clock) }
    val prefs: SharedPreferences get() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("sentinel", Context.MODE_PRIVATE)

    fun render(onboardingDone: Boolean = true, dark: Boolean = false, largeFont: Boolean = false) {
        prefs.edit().clear().putBoolean("onboarding_done", onboardingDone).commit()
        val context = RuntimeEnvironment.getApplication()
        startKoin {
            androidContext(context)
            modules(module {
                single<Clock> { data.clock }
                single<SharedPreferences> { prefs }
                single<SetupChecker> { setup }
                single<GlobalStateRepository> { data.global }
                single<AppConfigRepository> { data.apps }
                single<EventRepository> { data.events }
                single<EngineStatusRepository> { data.statuses }
                single<OverrideRepository> { data.overrides }
                single<UserRuleRepository> { data.users }
                single<GuardRunner> { guard }
                single<ColorOsProfile> { system.profile }
                single<OpExecutor> { system.executor }
                factory { keep(HomeViewModel(data.global, data.events, data.statuses,
                    data.overrides, data.apps, data.clock, privateDnsWarning = { null })) }
                factory { keep(AppsViewModel(data.apps, data.events, data.clock)) }
                factory { (pkg: String) -> keep(AppDetailViewModel(pkg, data.apps, data.events, data.clock)) }
                factory { keep(OnboardingViewModel(setup, prefs)) }
                factory { keep(SystemCleanupViewModel(system.profile, system.executor)) }
                factory { keep(RulesViewModel(MutableStateFlow(emptyList<SubscriptionEntity>()),
                    data.users, saveSubscription = {}, updateAll = { UpdateReport(0, 0, 0) },
                    rebuildFromCache = { 0L })) }
            })
        }
        compose.setContent {
            val config = DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 800.dp))
                .then(DeviceConfigurationOverride.FontScale(if (largeFont) 1.3f else 1f))
                .then(DeviceConfigurationOverride.DarkMode(dark))
            DeviceConfigurationOverride(config) {
                SentinelTheme {
                    navigation = rememberNavController()
                    SentinelNavHost(navController = navigation)
                }
            }
        }
        compose.waitForIdle()
    }

    fun navigate(route: String) {
        compose.runOnIdle { navigation.navigate(route) }
        compose.waitForIdle()
    }

    fun openApps() { compose.onNodeWithTag("nav:apps").performClick(); compose.waitForIdle() }
    fun openDetail(pkg: String) {
        openApps(); compose.onNodeWithTag("app:$pkg").performClick(); compose.waitForIdle()
        compose.onNodeWithTag("screen:app-detail").assertIsDisplayed()
    }
    fun openSystem() {
        compose.onNodeWithTag("home:system-cleanup").performClick(); compose.waitForIdle()
        compose.onNodeWithTag("screen:system-cleanup").assertIsDisplayed()
    }
}
