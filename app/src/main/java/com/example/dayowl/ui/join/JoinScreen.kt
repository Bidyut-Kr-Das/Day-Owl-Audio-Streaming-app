package com.example.dayowl.ui.join

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.dayowl.repository.ConnectionState
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
    val connectionState by viewModel.connectionState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Join Session",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                RadarSearchingView()
                
                // Discovered Host Avatars
                sessions.forEachIndexed { index, session ->
                    val angle = (index * (360f / sessions.size.coerceAtLeast(1))) * (Math.PI / 180f)
                    val radius = 100.dp
                    
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (radius.value * cos(angle)).toFloat().dp,
                                y = (radius.value * sin(angle)).toFloat().dp
                            )
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { viewModel.joinSession(session) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = session.sessionName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                if (sessions.isEmpty()) {
                    Text(
                        "Searching for hosts...",
                        modifier = Modifier.padding(top = 240.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (sessions.isNotEmpty()) {
                Text(
                    "Found ${sessions.size} host(s)",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f)
                ) {
                    items(sessions) { session ->
                        val isJoined = activeSession?.hostName == session.hostName
                        ListItem(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .clickable { viewModel.joinSession(session) },
                            headlineContent = { Text(session.sessionName, fontWeight = FontWeight.Bold) },
                            supportingContent = { 
                                Text(
                                    when {
                                        isJoined -> "Connected"
                                        connectionState == ConnectionState.CONNECTING -> "Connecting..."
                                        else -> "${session.ipAddress}:${session.port}"
                                    }
                                )
                            },
                            leadingContent = {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = session.sessionName.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
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
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Leave")
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.joinSession(session) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
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
