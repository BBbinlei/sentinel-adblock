package com.sentinel.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import com.sentinel.app.home.HomeScreen
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
            composable(Routes.HOME) { HomeScreen() }
            composable(Routes.APPS) { Placeholder("screen:apps", R.string.tab_apps) }
            composable(Routes.RULES) { Placeholder("screen:rules", R.string.tab_rules) }
        }
    }
}

@Composable
private fun Placeholder(tag: String, label: Int) {
    Box(Modifier.fillMaxSize().testTag(tag), contentAlignment = Alignment.Center) {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
    }
}
