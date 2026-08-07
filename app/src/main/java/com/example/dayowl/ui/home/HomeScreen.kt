package com.example.dayowl.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToHost: () -> Unit,
    onNavigateToJoin: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Day Owl") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onNavigateToHost,
                modifier = Modifier.fillMaxWidth(0.7f).padding(8.dp)
            ) {
                Text("Host Session")
            }
            Button(
                onClick = onNavigateToJoin,
                modifier = Modifier.fillMaxWidth(0.7f).padding(8.dp)
            ) {
                Text("Join Session")
            }
            OutlinedButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth(0.7f).padding(8.dp)
            ) {
                Text("Settings")
            }
        }
    }
}
