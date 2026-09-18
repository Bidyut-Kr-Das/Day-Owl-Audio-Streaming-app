package com.example.dayowl.ui.session

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dayowl.ui.components.DayOwlTopBar
import com.example.dayowl.ui.components.StatRow
import com.example.dayowl.ui.theme.Spacing

@Composable
fun SessionScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDebug: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DayOwlTopBar(
                title = "Session",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = onNavigateToDebug) {
                        Icon(Icons.Default.BugReport, contentDescription = "Debug")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Stream",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.lg))

            val shape = MaterialTheme.shapes.medium
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    StatRow(label = "Bitrate", value = null)
                    StatRow(label = "Connected listeners", value = null)
                    StatRow(label = "Packet loss", value = null)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = "These are not measured on this screen yet. Real counters exist only in " +
                    "logcat today, so nothing is shown rather than a number the app has not read.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Close", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}
