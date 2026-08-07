package com.example.dayowl.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.dayowl.ui.home.HomeScreen
import com.example.dayowl.ui.host.HostScreen
import com.example.dayowl.ui.join.JoinScreen
import com.example.dayowl.ui.session.SessionScreen
import com.example.dayowl.ui.debug.DebugScreen
import com.example.dayowl.ui.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    onRequestProjection: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToHost = { navController.navigate(Screen.Host.route) },
                onNavigateToJoin = { navController.navigate(Screen.Join.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Host.route) {
            HostScreen(
                onNavigateBack = { navController.popBackStack() },
                onRequestProjection = onRequestProjection
            )
        }
        composable(Screen.Join.route) {
            JoinScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSession = { navController.navigate(Screen.Session.route) }
            )
        }
        composable(Screen.Session.route) {
            SessionScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDebug = { navController.navigate(Screen.Debug.route) }
            )
        }
        composable(Screen.Debug.route) {
            DebugScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
