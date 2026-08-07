package com.example.dayowl.ui.join

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSession: () -> Unit = {},
    viewModel: JoinViewModel = koinViewModel()
) {
    val sessions by viewModel.discoveredSessions.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Session") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Searching for hosts...")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sessions) { session ->
                        val isJoined = activeSession?.hostName == session.hostName
                        ListItem(
                            headlineContent = { Text(session.sessionName) },
                            supportingContent = { 
                                Text(if (isJoined) "Connected" else "${session.ipAddress}:${session.port}") 
                            },
                            trailingContent = {
                                if (isJoined) {
                                    Row {
                                        TextButton(onClick = onNavigateToSession) {
                                            Text("Open")
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Button(
                                            onClick = { viewModel.leaveSession() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("Leave")
                                        }
                                    }
                                } else {
                                    Button(onClick = { viewModel.joinSession(session) }) {
                                        Text("Join")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
