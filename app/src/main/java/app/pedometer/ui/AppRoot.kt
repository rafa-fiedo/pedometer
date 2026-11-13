package app.pedometer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.pedometer.PedometerApp
import app.pedometer.R
import app.pedometer.counting.StepCounterService
import app.pedometer.ui.history.HistoryScreen
import app.pedometer.ui.onboarding.BatteryPrompt
import app.pedometer.ui.onboarding.NoSensorScreen
import app.pedometer.ui.onboarding.PermissionScreen
import app.pedometer.ui.onboarding.StartupScreen
import app.pedometer.ui.onboarding.hasActivityPermission
import app.pedometer.ui.onboarding.startupScreen
import app.pedometer.ui.scores.ScoresScreen
import app.pedometer.ui.settings.SettingsScreen
import app.pedometer.ui.today.TodayScreen
import kotlinx.coroutines.launch

private const val SETTINGS_ROUTE = "settings"

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    TODAY("today", R.string.tab_today, Icons.AutoMirrored.Filled.DirectionsWalk),
    HISTORY("history", R.string.tab_history, Icons.Filled.CalendarMonth),
    SCORES("scores", R.string.tab_scores, Icons.Filled.EmojiEvents),
}

/** Chooses between the no-sensor message, the permission screen and the main tabs. */
@Composable
fun AppRoot(skipSensorCheck: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as PedometerApp
    val scope = rememberCoroutineScope()
    val hasSensor = remember { StepCounterService.hasStepSensor(context) }
    var hasPermission by remember { mutableStateOf(hasActivityPermission(context)) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (startupScreen(hasSensor, hasPermission, skipSensorCheck)) {
            StartupScreen.NO_SENSOR -> NoSensorScreen()
            StartupScreen.PERMISSION -> PermissionScreen(
                onGranted = {
                    hasPermission = true
                    scope.launch { app.countingController.apply() }
                },
            )
            StartupScreen.MAIN -> MainTabs()
        }
    }
}

@Composable
private fun MainTabs() {
    val repository = (LocalContext.current.applicationContext as PedometerApp).repository
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // Settings is a full screen of its own, without the tabs.
            if (currentRoute != SETTINGS_ROUTE) NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.TODAY.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Tab.TODAY.route) {
                TodayScreen(onOpenSettings = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } })
            }
            composable(SETTINGS_ROUTE) { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable(Tab.HISTORY.route) { HistoryScreen() }
            composable(Tab.SCORES.route) { ScoresScreen() }
        }
    }

    BatteryPrompt(repository)
}
