package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.Screen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DeviceAutomationScreen
import com.example.ui.screens.LiveVoiceScreen
import com.example.ui.screens.MemoriesScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AlyaAssistantTheme
import com.example.ui.viewmodel.AlyaViewModel
import com.example.ui.screens.components.PrivacyPolicyDialog
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Firebase

class MainActivity : ComponentActivity() {

    private val viewModel: AlyaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.util.configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            AlyaAssistantTheme {
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val isPrivacyAccepted by viewModel.isPrivacyPolicyAccepted.collectAsStateWithLifecycle()
                
                var currentUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { auth -> 
                        currentUser = auth.currentUser 
                    }
                    FirebaseAuth.getInstance().addAuthStateListener(listener)
                    onDispose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
                }

                if (currentUser == null) {
                    com.example.ui.screens.SignInScreen(onAuthSuccess = { currentUser = FirebaseAuth.getInstance().currentUser })
                } else {
                    LaunchedEffect(Unit) {
                        val hasMic = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        viewModel.setAudioPermissionGranted(hasMic)
                    }

                    if (!isPrivacyAccepted) {
                        PrivacyPolicyDialog(onAccept = { viewModel.acceptPrivacyPolicy() })
                    }

                    // Handle system back navigation cleanly
                    if (currentScreen != Screen.CHAT) {
                        BackHandler {
                            if (currentScreen == Screen.LIVE_VOICE) {
                                viewModel.stopLiveSession()
                            }
                            viewModel.navigateTo(Screen.CHAT)
                        }
                    }

                    Surface(modifier = Modifier.fillMaxSize()) {
                        when (currentScreen) {
                            Screen.CHAT -> ChatScreen(viewModel = viewModel)
                            Screen.LIVE_VOICE -> LiveVoiceScreen(viewModel = viewModel)
                            Screen.MEMORIES -> MemoriesScreen(viewModel = viewModel)
                            Screen.DEVICE_AUTOMATION -> DeviceAutomationScreen(viewModel = viewModel)
                            Screen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            Screen.PERMISSIONS -> PermissionsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopLiveSession()
    }
}
