package com.example.dayowl.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Group
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String = "", val icon: ImageVector? = null) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Host : Screen("host", "Host")
    object Join : Screen("join", "Join", Icons.Default.Group)
    object Session : Screen("session", "Session")
    object Debug : Screen("debug", "Debug")
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}
