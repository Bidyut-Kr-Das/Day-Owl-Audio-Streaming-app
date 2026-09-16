package com.example.dayowl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.dayowl.ui.home.HomeScreen
import com.example.dayowl.ui.home.HomeViewModel
import com.example.dayowl.ui.host.HostScreen
import com.example.dayowl.ui.join.JoinScreen
import com.example.dayowl.ui.session.SessionScreen
import com.example.dayowl.ui.debug.DebugScreen
import com.example.dayowl.ui.settings.SettingsScreen
import com.example.dayowl.ui.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    onRequestProjection: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = koinViewModel()
            HomeScreen(
                viewModel = viewModel,
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
            val viewModel: SettingsViewModel = koinViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
