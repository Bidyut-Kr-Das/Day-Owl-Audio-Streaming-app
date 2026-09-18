package com.example.dayowl.ui.join

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dayowl.model.SessionInfo
import com.example.dayowl.repository.ConnectionState
import com.example.dayowl.ui.components.DayOwlTopBar
import com.example.dayowl.ui.components.HostAvatar
import com.example.dayowl.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun JoinScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSession: () -> Unit = {},
    viewModel: JoinViewModel = koinViewModel()
) {
    val sessions by viewModel.discoveredSessions.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DayOwlTopBar(
                title = "Join",
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        // The radar and the list showed the same hosts twice. The radar is the empty state; once a
        // host answers, the list is the content.
        if (sessions.isEmpty()) {
            SearchingState(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.sm,
                    bottom = Spacing.xxl
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item {
                    Text(
                        text = if (sessions.size == 1) "1 host nearby" else "${sessions.size} hosts nearby",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.sm)
                    )
                }
                items(sessions) { session ->
                    HostRow(
                        session = session,
                        isJoined = activeSession?.hostName == session.hostName,
                        isConnecting = connectionState == ConnectionState.CONNECTING,
                        onJoin = { viewModel.joinSession(session) },
                        onLeave = { viewModel.leaveSession() },
                        onOpen = onNavigateToSession
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RadarSearchingView()
        Spacer(modifier = Modifier.height(Spacing.xl))
        Text(
            text = "Looking for hosts",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = "Both phones need to be on the same WiFi network.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HostRow(
    session: SessionInfo,
    isJoined: Boolean,
    isConnecting: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onOpen: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = !isJoined) { onJoin() },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HostAvatar(name = session.sessionName)
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.sessionName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = when {
                        isJoined -> "Connected"
                        isConnecting -> "Connecting\u2026"
                        else -> "${session.ipAddress}:${session.port}"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = if (isJoined) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            if (isJoined) {
                TextButton(onClick = onOpen) {
                    Text("Open")
                }
                Spacer(modifier = Modifier.width(Spacing.xs))
                Button(
                    onClick = onLeave,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Leave")
                }
            } else {
                Button(
                    onClick = onJoin,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Join")
                }
            }
        }
    }
}
