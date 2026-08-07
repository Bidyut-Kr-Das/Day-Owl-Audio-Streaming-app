package com.example.dayowl.ui

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Host : Screen("host")
    object Join : Screen("join")
    object Session : Screen("session")
    object Debug : Screen("debug")
    object Settings : Screen("settings")
}
