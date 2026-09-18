package com.example.dayowl.ui.host

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dayowl.ui.components.DayOwlTopBar
import com.example.dayowl.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun HostScreen(
    onNavigateBack: () -> Unit,
    onRequestProjection: () -> Unit,
    viewModel: HostViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HostViewModel.HostEvent.RequestProjection -> onRequestProjection()
            }
        }
    }

    val isBroadcasting by viewModel.isBroadcasting.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DayOwlTopBar(
                title = "Host",
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isBroadcasting) {
                LiveIndicator()
                Spacer(modifier = Modifier.height(Spacing.lg))
            }

            Text(
                text = if (isBroadcasting) "Broadcasting" else "Not broadcasting",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = if (isBroadcasting) {
                    "Whatever this phone plays is going out to everyone who joined. " +
                        "Keep the app open with the screen on for the lowest jitter."
                } else {
                    "Android asks for the screen-capture prompt once per session \u2014 that is how it " +
                        "gates audio capture. Apps that block capture will arrive silent."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            if (isBroadcasting) {
                Button(
                    onClick = { viewModel.stopBroadcast() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Stop broadcast", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(
                    onClick = { viewModel.onStartClicked() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Start broadcast", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Amber is the one signal colour in the app and this is the only place it appears. */
@Composable
private fun LiveIndicator() {
    val transition = rememberInfiniteTransition(label = "LiveIndicator")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(pulse)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
