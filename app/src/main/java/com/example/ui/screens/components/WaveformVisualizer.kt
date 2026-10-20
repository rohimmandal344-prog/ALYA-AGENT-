package com.example.ui.screens.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AlyaRuby
import com.example.ui.theme.AlyaSapphire
import kotlin.random.Random

@Composable
fun WaveformVisualizer(
    modifier: Modifier = Modifier,
    amplitude: Float,
    barCount: Int = 28,
    activeColor: Color = AlyaRuby,
    height: Dp = 72.dp
) {
    val animatedAmp = remember { Animatable(0f) }

    LaunchedEffect(amplitude) {
        animatedAmp.animateTo(
            targetValue = amplitude.coerceIn(0.05f, 1f),
            animationSpec = tween(120, easing = FastOutSlowInEasing)
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val width = size.width
        val canvasHeight = size.height
        val totalSpacing = width * 0.25f
        val barWidth = (width - totalSpacing) / barCount
        val spacing = totalSpacing / (barCount - 1).coerceAtLeast(1)
        val centerY = canvasHeight / 2f

        val currentVal = animatedAmp.value

        for (i in 0 until barCount) {
            val progress = i.toFloat() / barCount
            // Bell curve envelope + randomized harmonic movement
            val envelope = (1f - 4f * (progress - 0.5f) * (progress - 0.5f)).coerceAtLeast(0.15f)
            val jitter = 0.7f + (Random(i * 31).nextFloat() * 0.6f)
            val barHeight = (canvasHeight * 0.85f * currentVal * envelope * jitter).coerceIn(6f, canvasHeight)

            val x = i * (barWidth + spacing)
            val top = centerY - (barHeight / 2f)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.9f),
                        AlyaSapphire.copy(alpha = 0.7f)
                    ),
                    startY = top,
                    endY = top + barHeight
                ),
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
