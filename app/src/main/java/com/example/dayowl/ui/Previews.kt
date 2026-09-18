package com.example.dayowl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.dayowl.ui.components.DayOwlTopBar
import com.example.dayowl.ui.components.HostAvatar
import com.example.dayowl.ui.components.StatRow
import com.example.dayowl.ui.debug.DebugScreen
import com.example.dayowl.ui.home.ActionCard
import com.example.dayowl.ui.home.HeaderSection
import com.example.dayowl.ui.session.SessionScreen
import com.example.dayowl.ui.theme.DayOwlTheme
import com.example.dayowl.ui.theme.Spacing

/**
 * Both schemes, side by side. Home, Join, Host and Settings take Koin ViewModels and are checked on
 * device instead; everything they are built from is previewed here.
 */
@Preview(name = "Components dark", showBackground = true)
@Composable
private fun ComponentsDarkPreview() = DayOwlTheme(darkTheme = true) { ComponentSheet() }

@Preview(name = "Components light", showBackground = true)
@Composable
private fun ComponentsLightPreview() = DayOwlTheme(darkTheme = false) { ComponentSheet() }

@Preview(name = "Session dark", showBackground = true)
@Composable
private fun SessionDarkPreview() = DayOwlTheme(darkTheme = true) {
    SessionScreen(onNavigateBack = {}, onNavigateToDebug = {})
}

@Preview(name = "Session light", showBackground = true)
@Composable
private fun SessionLightPreview() = DayOwlTheme(darkTheme = false) {
    SessionScreen(onNavigateBack = {}, onNavigateToDebug = {})
}

@Preview(name = "Debug dark", showBackground = true)
@Composable
private fun DebugDarkPreview() = DayOwlTheme(darkTheme = true) { DebugScreen(onNavigateBack = {}) }

@Preview(name = "Debug light", showBackground = true)
@Composable
private fun DebugLightPreview() = DayOwlTheme(darkTheme = false) { DebugScreen(onNavigateBack = {}) }

@Composable
private fun ComponentSheet() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            DayOwlTopBar(title = "Day Owl", onNavigateBack = {})
            HeaderSection(username = "Bidyut")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                ActionCard(
                    title = "Host",
                    subtitle = "Share this phone's audio",
                    icon = Icons.Default.Cast,
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
                ActionCard(
                    title = "Join",
                    subtitle = "Listen to someone nearby",
                    icon = Icons.Default.GroupAdd,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }
            HostAvatar(name = "Living room")
            StatRow(label = "Average latency", value = null)
            StatRow(label = "Sample rate", value = "48 kHz")
        }
    }
}
