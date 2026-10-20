package com.example.ui.screens.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.R
import com.example.data.live.LiveConnectionState
import com.example.ui.theme.AlyaRuby
import com.example.ui.theme.AlyaSapphire
import com.example.ui.theme.StateListeningColor
import com.example.ui.theme.StateSpeakingColor
import com.example.ui.theme.StateThinkingColor

@Composable
fun AlyaAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    liveState: LiveConnectionState = LiveConnectionState.IDLE,
    amplitude: Float = 0f
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val activeGlowColor = when (liveState) {
        LiveConnectionState.SPEAKING -> StateSpeakingColor
        LiveConnectionState.LISTENING -> StateListeningColor
        LiveConnectionState.THINKING -> StateThinkingColor
        else -> AlyaRuby
    }

    val dynamicScale = if (liveState == LiveConnectionState.SPEAKING || liveState == LiveConnectionState.LISTENING) {
        pulseScale + (amplitude * 0.15f)
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(dynamicScale),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow halo ring
        if (liveState == LiveConnectionState.SPEAKING || liveState == LiveConnectionState.LISTENING) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                activeGlowColor.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Inner Avatar image container
        Box(
            modifier = Modifier
                .size(size * 0.88f)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(listOf(AlyaRuby, AlyaSapphire)),
                    shape = CircleShape
                )
                .background(Color(0xFF1E1B4B)),
            contentAlignment = Alignment.Center
        ) {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(R.drawable.alya_avatar)
                    .crossfade(true)
                    .build()
            )

            if (painter.state is AsyncImagePainter.State.Success) {
                Image(
                    painter = painter,
                    contentDescription = "Alisa Mikhailovna Kujou (Alya)",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "Alya Portrait",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
    }
}
