package com.sentinel.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.sentinel.app.R
import com.sentinel.app.apps.AppDetailScreen
import com.sentinel.app.apps.AppsScreen
import com.sentinel.app.home.HomeScreen
import com.sentinel.app.log.EngineLogScreen
import com.sentinel.app.rules.RulesScreen
import com.sentinel.app.system.OpLogScreen
import com.sentinel.app.system.SystemCleanupScreen
import com.sentinel.data.db.EngineId
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.sentinel.app.onboarding.OnboardingScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val APPS = "apps"
    const val APP_DETAIL = "app_detail/{pkg}"
    const val RULES = "rules"
    const val SYSTEM_CLEANUP = "system_cleanup"
    const val ENGINE_LOG = "engine_log/{engine}"
    const val OP_LOG = "op_log"

    fun appDetail(pkg: String) = "app_detail/$pkg"
    fun engineLog(engine: String) = "engine_log/$engine"
}

private data class Tab(val route: String, val label: Int, val tag: String)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, "nav:home"),
    Tab(Routes.APPS, R.string.tab_apps, "nav:apps"),
    Tab(Routes.RULES, R.string.tab_rules, "nav:rules"),
)

@Composable
fun SentinelNavHost(navController: NavHostController, startDestination: String = Routes.HOME) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = current == null || tabs.any { it.route == current }

    Scaffold(
        topBar = {
            if (current in setOf(Routes.APP_DETAIL, Routes.SYSTEM_CLEANUP, Routes.ENGINE_LOG, Routes.OP_LOG)) {
                Box(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("nav:back"),
                    ) { Text(stringResource(R.string.nav_back)) }
                }
            }
        },
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            },
                            label = { Text(stringResource(tab.label)) },
                            modifier = Modifier.testTag(tab.tag),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = startDestination, modifier = Modifier.padding(padding)) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSystem = { navController.navigate(Routes.SYSTEM_CLEANUP) },
                    onOpenEngineLog = { navController.navigate(Routes.engineLog(it)) },
                    onOpenOpLog = { navController.navigate(Routes.OP_LOG) },
                    onOpenSetup = { navController.navigate(Routes.ONBOARDING) },
                )
            }
            composable(Routes.SYSTEM_CLEANUP) { SystemCleanupScreen(onOpenOpLog = { navController.navigate(Routes.OP_LOG) }) }
            composable(Routes.OP_LOG) { OpLogScreen() }
            composable(Routes.ENGINE_LOG, arguments = listOf(navArgument("engine") { type = NavType.StringType })) { entry ->
                val engine = EngineId.entries.firstOrNull { it.name == entry.arguments?.getString("engine") } ?: EngineId.VPN
                EngineLogScreen(engine)
            }
            composable(Routes.APPS) { AppsScreen(onOpen = { navController.navigate(Routes.appDetail(it)) }) }
            composable(Routes.APP_DETAIL, arguments = listOf(navArgument("pkg") { type = NavType.StringType })) { entry ->
                AppDetailScreen(pkg = entry.arguments?.getString("pkg").orEmpty())
            }
            composable(Routes.RULES) { RulesScreen() }
        }
    }
}

