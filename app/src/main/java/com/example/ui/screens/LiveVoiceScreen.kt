package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.live.LiveConnectionState
import com.example.ui.navigation.Screen
import com.example.ui.screens.components.AlyaAvatar
import com.example.ui.screens.components.WaveformVisualizer
import com.example.ui.theme.AlyaCardDark
import com.example.ui.theme.AlyaRuby
import com.example.ui.theme.AlyaSapphire
import com.example.ui.theme.AlyaSurfaceDark
import com.example.ui.theme.StateConnectingColor
import com.example.ui.theme.StateErrorColor
import com.example.ui.theme.StateIdleColor
import com.example.ui.theme.StateInterruptedColor
import com.example.ui.theme.StateListeningColor
import com.example.ui.theme.StateSpeakingColor
import com.example.ui.theme.StateThinkingColor
import com.example.ui.viewmodel.AlyaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVoiceScreen(viewModel: AlyaViewModel) {
    val context = LocalContext.current

    val liveState by viewModel.liveState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.liveStatusMessage.collectAsStateWithLifecycle()
    val isMicMuted by viewModel.isMicMuted.collectAsStateWithLifecycle()
    val inAmp by viewModel.inputAmplitude.collectAsStateWithLifecycle()
    val outAmp by viewModel.outputAmplitude.collectAsStateWithLifecycle()
    val transcript by viewModel.liveTranscript.collectAsStateWithLifecycle()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsStateWithLifecycle()
    val selectedVoice by viewModel.selectedVoice.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasMicPermission = granted
            viewModel.setAudioPermissionGranted(granted)
            if (granted) {
                viewModel.startLiveSession()
            }
        }
    )

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasMicPermission = granted
        viewModel.setAudioPermissionGranted(granted)
    }

    val currentAmplitude = if (liveState == LiveConnectionState.SPEAKING) outAmp else inAmp

    val stateColor = when (liveState) {
        LiveConnectionState.IDLE -> StateIdleColor
        LiveConnectionState.CONNECTING, LiveConnectionState.RECONNECTING -> StateConnectingColor
        LiveConnectionState.CONNECTED, LiveConnectionState.LISTENING -> StateListeningColor
        LiveConnectionState.THINKING -> StateThinkingColor
        LiveConnectionState.SPEAKING -> StateSpeakingColor
        LiveConnectionState.INTERRUPTED -> StateInterruptedColor
        LiveConnectionState.ERROR, LiveConnectionState.DISCONNECTED -> StateErrorColor
    }

    // The 4 requested voices from the user prompt: Aoede, Kore, Ursa, Cinder (Cinderella)
    val voiceChoices = listOf(
        Triple("Aoede", "एडो (Aoede)", "Warm & Natural (Alya)"),
        Triple("Kore", "कोडे (Kore)", "Gentle & Sweet"),
        Triple("Ursa", "उरसा (Ursa)", "Friendly & Calm"),
        Triple("Cinder", "सिंड्रेला (Cinder)", "Radiant & Clear")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(AlyaSapphire, AlyaRuby))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Gemini Live",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AlyaRuby.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        color = AlyaRuby,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Natural Voice Tone • 16kHz Bidirectional Audio",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.stopLiveSession()
                            viewModel.navigateTo(Screen.CHAT)
                        },
                        modifier = Modifier.testTag("live_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Chat",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.SETTINGS) },
                        modifier = Modifier.testTag("live_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Voice Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AlyaSurfaceDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AlyaSurfaceDark,
                            AlyaCardDark,
                            Color(0xFF090814)
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Permission Banner (if not granted)
            if (!hasMicPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1520)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE11D48))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFFFB7185))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Microphone Permission Required",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Gemini Live requires microphone permission to listen to your voice in real time.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Grant Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // API Key Missing Warning Banner
            if (!hasApiKey) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AlyaSapphire)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = AlyaSapphire)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gemini API Key Needed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Add your free key to unlock instant live voice.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        Button(
                            onClick = { viewModel.navigateTo(Screen.SETTINGS) },
                            colors = ButtonDefaults.buttonColors(containerColor = AlyaSapphire),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Add Key", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Connection Status Capsule
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = stateColor.copy(alpha = 0.16f),
                border = androidx.compose.foundation.BorderStroke(1.dp, stateColor.copy(alpha = 0.6f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (liveState == LiveConnectionState.CONNECTING || liveState == LiveConnectionState.RECONNECTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = stateColor
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(stateColor)
                        )
                    }
                    Text(
                        text = when (liveState) {
                            LiveConnectionState.CONNECTED, LiveConnectionState.LISTENING -> "Gemini Live: Listening..."
                            LiveConnectionState.CONNECTING -> "Connecting to Gemini Live..."
                            LiveConnectionState.RECONNECTING -> "Reconnecting..."
                            LiveConnectionState.SPEAKING -> "Alya Speaking (Natural Voice)"
                            LiveConnectionState.THINKING -> "Alya Thinking..."
                            LiveConnectionState.INTERRUPTED -> "Barge-in Interrupted"
                            LiveConnectionState.ERROR -> "Connection Error"
                            LiveConnectionState.DISCONNECTED -> "Session Ended"
                            else -> "Ready to Connect"
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Text(
                    text = statusMessage.orEmpty(),
                    color = if (liveState == LiveConnectionState.ERROR || liveState == LiveConnectionState.DISCONNECTED) StateErrorColor else Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Large Hero Animated Avatar with pulsating glow
            AlyaAvatar(
                size = 175.dp,
                liveState = liveState,
                amplitude = currentAmplitude
            )

            // Dynamic Real-time Audio Waveform
            WaveformVisualizer(
                amplitude = currentAmplitude,
                activeColor = stateColor,
                height = 56.dp
            )

            // Voice Selector Row: Aoede, Kore, Ursa, Cinder (Cinderella)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Voice Tone (जिमिनी वॉइस)",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    voiceChoices.forEach { (voiceId, voiceLabel, _) ->
                        val isSelected = selectedVoice.equals(voiceId, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) AlyaSapphire else Color(0x26FFFFFF)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.White else Color(0x33FFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.updateSelectedVoice(voiceId)
                                    if (liveState == LiveConnectionState.CONNECTED || liveState == LiveConnectionState.LISTENING) {
                                        viewModel.retryLiveSession()
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = voiceLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Live Subtitles / Real-time Transcript
            if (subtitlesEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x26FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (transcript.isNotBlank()) transcript
                            else if (liveState == LiveConnectionState.SPEAKING) "Alya is speaking..."
                            else "Live subtitle will appear here as you speak...",
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Floating Bottom Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute / Unmute Mic Button
                FilledTonalIconButton(
                    onClick = { viewModel.toggleMicMute() },
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("live_mic_mute_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isMicMuted) Color(0xFFDC2626) else Color(0x33FFFFFF),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMicMuted) "Unmute Mic" else "Mute Mic"
                    )
                }

                // Barge-in / Interrupt Speech button
                FilledTonalIconButton(
                    onClick = { viewModel.interruptLiveAudio() },
                    enabled = liveState == LiveConnectionState.SPEAKING,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("live_interrupt_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (liveState == LiveConnectionState.SPEAKING) AlyaRuby else Color(0x1AFFFFFF),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Interrupt / Barge-in"
                    )
                }

                // Connect / Reconnect Button
                FilledTonalIconButton(
                    onClick = {
                        if (!hasMicPermission) {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.startLiveSession()
                        }
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("live_reconnect_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (liveState == LiveConnectionState.ERROR || liveState == LiveConnectionState.DISCONNECTED || liveState == LiveConnectionState.IDLE) AlyaSapphire else Color(0x26FFFFFF),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Start / Reconnect Session"
                    )
                }

                // End Call button
                IconButton(
                    onClick = {
                        viewModel.stopLiveSession()
                        viewModel.navigateTo(Screen.CHAT)
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE11D48))
                        .testTag("live_end_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Live Call",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
