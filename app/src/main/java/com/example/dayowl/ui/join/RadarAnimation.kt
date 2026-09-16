package com.example.dayowl.ui.join

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RadarSearchingView(
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    baseSize: Dp = 200.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")

    // Animations for 3 waves with staggered starts
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave1"
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave2"
    )

    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Wave3"
    )

    Box(
        modifier = modifier.size(baseSize * 1.5f),
        contentAlignment = Alignment.Center
    ) {
        // Sonar Waves
        Canvas(modifier = Modifier.fillMaxSize()) {
            val waves = listOf(wave1, wave2, wave3)
            waves.forEach { progress ->
                drawCircle(
                    color = waveColor,
                    radius = (size.minDimension / 2) * progress,
                    alpha = (1f - progress).coerceIn(0f, 1f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Central Avatar/Device Icon
        Surface(
            modifier = Modifier.size(baseSize / 2.5f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(baseSize / 5f),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
