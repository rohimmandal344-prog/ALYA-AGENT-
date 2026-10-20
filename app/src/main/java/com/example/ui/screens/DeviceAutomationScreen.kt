package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Vibration
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.Screen
import com.example.ui.theme.AlyaRuby
import com.example.ui.theme.AlyaSapphire
import com.example.ui.theme.StateErrorColor
import com.example.ui.theme.StateListeningColor
import com.example.ui.viewmodel.AlyaViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceAutomationScreen(viewModel: AlyaViewModel) {
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastAutomationResult.collectAsStateWithLifecycle()
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Automation", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.CHAT) },
                        modifier = Modifier.testTag("automation_back_button")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Honest Capabilities Notice Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = AlyaRuby)
                        Column {
                            Text(text = "Honest System Capabilities", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = "Strictly bounded to official Android APIs. No fake full control.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    capabilities.forEach { cap ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = cap.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = cap.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (cap.isSupported) StateListeningColor.copy(alpha = 0.15f) else StateErrorColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = cap.statusText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (cap.isSupported) StateListeningColor else StateErrorColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Last Execution Result
            if (lastResult != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (lastResult?.success == true) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (lastResult?.success == true) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (lastResult?.success == true) StateListeningColor else StateErrorColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = lastResult?.message.orEmpty(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (lastResult?.success == true) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 3. Interactive Quick Triggers
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Manual Hardware Triggers", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Flashlight
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Flashlight / Torch", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(text = if (isFlashlightOn) "State: ON" else "State: OFF", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                viewModel.triggerAutomation("toggle_flashlight", mapOf("enabled" to !isFlashlightOn))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFlashlightOn) StateErrorColor else AlyaRuby
                            ),
                            modifier = Modifier.testTag("toggle_flashlight_button")
                        ) {
                            Icon(
                                imageVector = if (isFlashlightOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isFlashlightOn) "Turn Off" else "Turn On")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Battery Check
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Battery Status", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Read current capacity and charging", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { viewModel.triggerAutomation("get_battery_status") },
                            modifier = Modifier.testTag("check_battery_button")
                        ) {
                            Icon(imageVector = Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Inspect")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Volume Controls
                    Text(text = "Media Volume Adjust", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.triggerAutomation("adjust_device_volume", mapOf("action" to "UP")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume Up", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Up")
                        }
                        FilledTonalButton(
                            onClick = { viewModel.triggerAutomation("adjust_device_volume", mapOf("action" to "DOWN")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Volume Down", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Down")
                        }
                        FilledTonalButton(
                            onClick = { viewModel.triggerAutomation("adjust_device_volume", mapOf("action" to "MUTE")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.VolumeMute, contentDescription = "Mute", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mute")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Haptic & Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.triggerAutomation("trigger_vibration", mapOf("duration" to 300)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Vibrate")
                        }

                        OutlinedButton(
                            onClick = { viewModel.triggerAutomation("get_device_time") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Time")
                        }
                    }
                }
            }

            // 4. Safe App Launchers
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "App Shortcuts (Official Intents)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Alya can launch these installed apps safely upon voice/text request.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("YouTube", "Camera", "Calculator", "Maps", "Settings", "Clock").forEach { app ->
                            FilledTonalButton(
                                onClick = {
                                    viewModel.triggerAutomation("open_application", mapOf("app_name" to app.lowercase()))
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(app, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
