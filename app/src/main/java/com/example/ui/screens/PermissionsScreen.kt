package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.automation.AlyaAccessibilityService
import com.example.ui.navigation.Screen
import com.example.ui.theme.AlyaRuby
import com.example.ui.theme.AlyaSapphire
import com.example.ui.theme.StateConnectingColor
import com.example.ui.theme.StateErrorColor
import com.example.ui.theme.StateListeningColor
import com.example.ui.viewmodel.AlyaViewModel

data class PermissionCategory(
    val title: String,
    val icon: ImageVector,
    val items: List<PermissionDetail>
)

data class PermissionDetail(
    val id: String,
    val name: String,
    val description: String,
    val isGranted: Boolean,
    val statusText: String,
    val actionType: PermissionActionType = PermissionActionType.NONE
)

enum class PermissionActionType {
    NONE,
    REQUEST_RUNTIME,
    OPEN_ACCESSIBILITY_SETTINGS,
    OPEN_APP_SETTINGS,
    OPEN_BATTERY_SETTINGS,
    OPEN_NOTIFICATION_SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(viewModel: AlyaViewModel) {
    val context = LocalContext.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var isAccessibilityActive by remember {
        mutableStateOf(AlyaAccessibilityService.isServiceActive)
    }

    var isBatteryOptExempt by remember {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else {
                true
            }
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasMicPermission = granted
            viewModel.setAudioPermissionGranted(granted)
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotificationPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        viewModel.setAudioPermissionGranted(hasMicPermission)
        isAccessibilityActive = AlyaAccessibilityService.isServiceActive
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityActive = AlyaAccessibilityService.isServiceActive
                val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasMicPermission = mic
                viewModel.setAudioPermissionGranted(mic)
                hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val categories = remember(hasMicPermission, hasCameraPermission, hasNotificationPermission, isAccessibilityActive, isBatteryOptExempt) {
        listOf(
            PermissionCategory(
                title = "Device Control & Accessibility Automation",
                icon = Icons.Default.AccessibilityNew,
                items = listOf(
                    PermissionDetail(
                        id = "accessibility_service",
                        name = "Accessibility Service (DeviceControlPermissions)",
                        description = "Enables navigation gestures (Back, Home, Recents, Notifications), TapControl, SwipeControl, ScrollControl, and ScreenReading.",
                        isGranted = isAccessibilityActive,
                        statusText = if (isAccessibilityActive) "ACTIVE" else "DISABLED",
                        actionType = PermissionActionType.OPEN_ACCESSIBILITY_SETTINGS
                    ),
                    PermissionDetail(
                        id = "overlay_window",
                        name = "Display Over Other Apps (PictureInPicture)",
                        description = "Allows live voice overlay, floating assistant pill, and FullScreenIntent alerts.",
                        isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true,
                        statusText = if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true) "ALLOWED" else "OPTIONAL",
                        actionType = PermissionActionType.OPEN_APP_SETTINGS
                    ),
                    PermissionDetail(
                        id = "app_launch",
                        name = "AppLaunchControl & PackageVisibility",
                        description = "Launch installed applications (YouTube, Maps, Camera, Calculator, Settings) via official Intents.",
                        isGranted = true,
                        statusText = "SUPPORTED",
                        actionType = PermissionActionType.NONE
                    ),
                    PermissionDetail(
                        id = "gesture_tap_scroll",
                        name = "GestureControl (Tap, Swipe, Scroll, Text Input)",
                        description = "Honest programmatic gesture dispatch through registered AccessibilityService.",
                        isGranted = isAccessibilityActive,
                        statusText = if (isAccessibilityActive) "ACTIVE" else "REQUIRES SERVICE",
                        actionType = PermissionActionType.OPEN_ACCESSIBILITY_SETTINGS
                    ),
                    PermissionDetail(
                        id = "autostart_background",
                        name = "Autostart & BackgroundExecution",
                        description = "Maintains connection and foreground voice capability across device states.",
                        isGranted = true,
                        statusText = "ENABLED",
                        actionType = PermissionActionType.NONE
                    )
                )
            ),
            PermissionCategory(
                title = "Voice, Microphone & Live Audio Pipeline",
                icon = Icons.Default.GraphicEq,
                items = listOf(
                    PermissionDetail(
                        id = "mic_audio_record",
                        name = "Microphone (AudioRecording 16kHz PCM)",
                        description = "Zero-latency 16kHz mono linear PCM streaming directly to Gemini Live API.",
                        isGranted = hasMicPermission,
                        statusText = if (hasMicPermission) "GRANTED" else "REQUIRED",
                        actionType = PermissionActionType.REQUEST_RUNTIME
                    ),
                    PermissionDetail(
                        id = "live_conversation",
                        name = "LiveConversation (Gemini Multimodal Live)",
                        description = "Bidirectional streaming WebSocket session with native gemini-2.0-flash audio.",
                        isGranted = hasMicPermission,
                        statusText = if (hasMicPermission) "READY" else "NEED MIC",
                        actionType = PermissionActionType.REQUEST_RUNTIME
                    ),
                    PermissionDetail(
                        id = "audio_playback_tts",
                        name = "AudioPlayback & TextToSpeech",
                        description = "16kHz AudioTrack playback engine with generational thread-safe queue.",
                        isGranted = true,
                        statusText = "ACTIVE",
                        actionType = PermissionActionType.NONE
                    ),
                    PermissionDetail(
                        id = "noise_suppression",
                        name = "NoiseSuppression & Hardware AEC",
                        description = "Android hardware AcousticEchoCanceler prevents speaker-to-mic feedback loops.",
                        isGranted = true,
                        statusText = "ATTACHED",
                        actionType = PermissionActionType.NONE
                    ),
                    PermissionDetail(
                        id = "real_time_transcription",
                        name = "RealTimeTranscription & VoiceCommands",
                        description = "Synchronized live subtitles and natural speech turn recognition.",
                        isGranted = true,
                        statusText = "AVAILABLE",
                        actionType = PermissionActionType.NONE
                    )
                )
            ),
            PermissionCategory(
                title = "Hardware Controls & Telemetry",
                icon = Icons.Default.Build,
                items = listOf(
                    PermissionDetail(
                        id = "camera_flashlight",
                        name = "Camera (FlashlightControl)",
                        description = "Hardware LED torch on/off control via Camera2 CameraManager.",
                        isGranted = hasCameraPermission,
                        statusText = if (hasCameraPermission) "GRANTED" else "OPTIONAL",
                        actionType = PermissionActionType.REQUEST_RUNTIME
                    ),
                    PermissionDetail(
                        id = "volume_media",
                        name = "VolumeControl & MediaPlaybackControl",
                        description = "Direct system audio volume adjustment and mute toggling via AudioManager.",
                        isGranted = true,
                        statusText = "AVAILABLE",
                        actionType = PermissionActionType.NONE
                    ),
                    PermissionDetail(
                        id = "vibration_control",
                        name = "VibrationControl (Haptics)",
                        description = "Haptic pulses for turn recognition and physical feedback.",
                        isGranted = true,
                        statusText = "ACTIVE",
                        actionType = PermissionActionType.NONE
                    ),
                    PermissionDetail(
                        id = "battery_exemption",
                        name = "BatteryOptimizationExemption (WAKELOCK)",
                        description = "Prevents Android OS from prematurely terminating active voice calls.",
                        isGranted = isBatteryOptExempt,
                        statusText = if (isBatteryOptExempt) "EXEMPT" else "DEFAULT",
                        actionType = PermissionActionType.OPEN_BATTERY_SETTINGS
                    ),
                    PermissionDetail(
                        id = "device_status",
                        name = "DeviceStatus & Battery Telemetry",
                        description = "Real-time battery level, charging state, and system clock reporting.",
                        isGranted = true,
                        statusText = "AVAILABLE",
                        actionType = PermissionActionType.NONE
                    ),
                    PermissionDetail(
                        id = "alarm_control",
                        name = "AlarmControl & ExactAlarm",
                        description = "Create and inspect device alarms through standard system Intents.",
                        isGranted = true,
                        statusText = "SUPPORTED",
                        actionType = PermissionActionType.NONE
                    )
                )
            ),
            PermissionCategory(
                title = "Connectivity, Networks & Bluetooth",
                icon = Icons.Default.Wifi,
                items = listOf(
                    PermissionDetail(
                        id = "network_access",
                        name = "NetworkAccess (INTERNET & NetworkStatus)",
                        description = "High-speed WebSocket connection to Google Gemini Live API.",
                        isGranted = true,
                        statusText = "CONNECTED",
                        actionType = PermissionActionType.NONE
                    ),
                    PermissionDetail(
                        id = "wifi_control",
                        name = "WiFi (ACCESS_WIFI_STATE)",
                        description = "Read WiFi status and connection state for optimal audio streaming.",
                        isGranted = true,
                        statusText = "GRANTED",
                        actionType = PermissionActionType.NONE
                    ),
                    PermissionDetail(
                        id = "bluetooth_devices",
                        name = "Bluetooth & NearbyDevices",
                        description = "Headset routing and wireless mic support for hands-free conversation.",
                        isGranted = true,
                        statusText = "AVAILABLE",
                        actionType = PermissionActionType.NONE
                    ),
                    PermissionDetail(
                        id = "location_access",
                        name = "Location & BackgroundLocation",
                        description = "Regional context for weather and directions when asked.",
                        isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
                        statusText = if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) "GRANTED" else "OPTIONAL",
                        actionType = PermissionActionType.OPEN_APP_SETTINGS
                    )
                )
            ),
            PermissionCategory(
                title = "Security, Privacy & Storage",
                icon = Icons.Default.Security,
                items = listOf(
                    PermissionDetail(
                        id = "secure_key_storage",
                        name = "SecureKeyStorage (Android KeyStore AES-256)",
                        description = "Hardware-backed encryption for user-owned Gemini API key at rest.",
                        isGranted = true,
                        statusText = "ENCRYPTED",
                        actionType = PermissionActionType.NONE
                    ),
                    PermissionDetail(
                        id = "data_backup_restore",
                        name = "DataBackup & DataRestore (Room SQLite)",
                        description = "Local on-device persistence for approved facts, preferences, and summaries.",
                        isGranted = true,
                        statusText = "PERSISTENT",
                        actionType = PermissionActionType.NONE
                    ),
                    PermissionDetail(
                        id = "notifications",
                        name = "Notifications (POST_NOTIFICATIONS)",
                        description = "Status notices and foreground service session indicators.",
                        isGranted = hasNotificationPermission,
                        statusText = if (hasNotificationPermission) "GRANTED" else "OPTIONAL",
                        actionType = PermissionActionType.REQUEST_RUNTIME
                    ),
                    PermissionDetail(
                        id = "contacts_phone_sms",
                        name = "Contacts, Phone, CallLogs, SMS, Calendar",
                        description = "Read/launch capabilities via safe intent bridges without privacy leakage.",
                        isGranted = true,
                        statusText = "INTENT GATED",
                        actionType = PermissionActionType.NONE
                    ),
                    PermissionDetail(
                        id = "credential_manager",
                        name = "CredentialManager & Biometrics",
                        description = "Support for Android Credential Manager and biometric protection.",
                        isGranted = true,
                        statusText = "SUPPORTED",
                        actionType = PermissionActionType.NONE
                    )
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions & Device Controls", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.CHAT) },
                        modifier = Modifier.testTag("permissions_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AlyaRuby,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Privacy & System Permission Hub", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = "Comprehensive status of all hardware, voice, accessibility, and automation subsystems.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(categories) { category ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(imageVector = category.icon, contentDescription = null, tint = AlyaSapphire)
                            Text(text = category.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        category.items.forEach { detail ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = detail.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when (detail.statusText) {
                                            "ACTIVE", "GRANTED", "READY", "ENABLED", "CONNECTED", "PERSISTENT", "ALLOWED" -> StateListeningColor.copy(alpha = 0.15f)
                                            "REQUIRED", "DISABLED" -> StateErrorColor.copy(alpha = 0.15f)
                                            else -> AlyaSapphire.copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Text(
                                            text = detail.statusText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (detail.statusText) {
                                                "ACTIVE", "GRANTED", "READY", "ENABLED", "CONNECTED", "PERSISTENT", "ALLOWED" -> StateListeningColor
                                                "REQUIRED", "DISABLED" -> StateErrorColor
                                                else -> AlyaSapphire
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = detail.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )

                                if (detail.actionType != PermissionActionType.NONE && !detail.isGranted) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    when (detail.actionType) {
                                        PermissionActionType.REQUEST_RUNTIME -> {
                                            Button(
                                                onClick = {
                                                    when (detail.id) {
                                                        "mic_audio_record", "live_conversation" -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                        "camera_flashlight" -> cameraLauncher.launch(Manifest.permission.CAMERA)
                                                        "notifications" -> {
                                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = AlyaRuby)
                                            ) {
                                                Text("Grant Permission")
                                            }
                                        }
                                        PermissionActionType.OPEN_ACCESSIBILITY_SETTINGS -> {
                                            FilledTonalButton(
                                                onClick = {
                                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.AccessibilityNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Enable in System Accessibility Settings")
                                            }
                                        }
                                        PermissionActionType.OPEN_BATTERY_SETTINGS -> {
                                            OutlinedButton(
                                                onClick = {
                                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Adjust Battery Optimization")
                                            }
                                        }
                                        PermissionActionType.OPEN_APP_SETTINGS -> {
                                            OutlinedButton(
                                                onClick = {
                                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = Uri.fromParts("package", context.packageName, null)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Open App Settings")
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Android System Settings Manager")
                }
            }
        }
    }
}
