package com.example.dayowl.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.dayowl.ui.components.DayOwlTopBar
import com.example.dayowl.ui.components.StatRow
import com.example.dayowl.ui.theme.Spacing

@Composable
fun DebugScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DayOwlTopBar(
                title = "Debug",
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xxl
            )
        ) {
            item {
                Text(
                    text = "Runtime counters live in logcat and are not read back into the UI. " +
                        "Rows stay blank until they are.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.lg)
                )
            }

            section("Capture", listOf("Capture status", "Bytes captured"))
            section("Transport", listOf("Packets sent", "Packets received", "Dropped packets"))
            section("Buffer", listOf("Average latency", "Jitter", "Buffer fill"))
        }
    }
}

private fun LazyListScope.section(title: String, labels: List<String>) {
    item {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    items(labels.size) { index ->
        StatRow(label = labels[index], value = null)
    }
    item { Spacer(modifier = Modifier.height(Spacing.lg)) }
}
