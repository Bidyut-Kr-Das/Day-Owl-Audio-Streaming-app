package com.example.dayowl.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.dayowl.ui.theme.Spacing

@Composable
fun MainScaffold(
    navController: NavHostController,
    onRequestProjection: () -> Unit
) {
    val navItems = listOf(
        Screen.Home,
        Screen.Join,
        Screen.Settings
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            // Only show bottom nav on main screens
            if (navItems.any { it.route == currentDestination?.route }) {
                val pillShape = MaterialTheme.shapes.extraLarge
                Surface(
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .shadow(6.dp, pillShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, pillShape),
                    shape = pillShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(72.dp)
                    ) {
                        navItems.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                icon = {
                                    screen.icon?.let {
                                        Icon(
                                            imageVector = it,
                                            contentDescription = screen.label
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = screen.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                selected = selected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            onRequestProjection = onRequestProjection,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        )
    }
}
