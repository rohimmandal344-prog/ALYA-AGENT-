package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.Screen
import com.example.ui.theme.AlyaRuby
import com.example.ui.theme.AlyaSapphire
import com.example.ui.theme.StateErrorColor
import com.example.ui.theme.StateListeningColor
import com.example.ui.viewmodel.AlyaViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.example.data.model.AlyaLanguage
import com.example.data.model.LanguageCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AlyaViewModel) {
    val apiKeyInput by viewModel.apiKeyInput.collectAsStateWithLifecycle()
    val maskedKey by viewModel.maskedApiKey.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val isValidating by viewModel.isValidatingKey.collectAsStateWithLifecycle()
    val validationMsg by viewModel.keyValidationMessage.collectAsStateWithLifecycle()
    val isKeyValid by viewModel.isKeyValid.collectAsStateWithLifecycle()

    val selectedVoice by viewModel.selectedVoice.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsStateWithLifecycle()

    val conversationSessions by viewModel.conversationSessions.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val userSettingsProfile by viewModel.userSettingsProfile.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    LaunchedEffect(validationMsg) {
        if (isKeyValid == false && !validationMsg.isNullOrBlank()) {
            Toast.makeText(context, validationMsg, Toast.LENGTH_LONG).show()
        }
    }

    var showKeyText by remember { mutableStateOf(false) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var languageSearchQuery by remember { mutableStateOf("") }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val voices = listOf(
        "Aoede" to "एडो (Aoede) • Warm & Expressive (Alya's Voice)",
        "Kore" to "कोडे (Kore) • Gentle, Bright & Sweet",
        "Ursa" to "उरसा (Ursa) • Natural, Friendly & Calm",
        "Cinder" to "सिंड्रेला (Cinder) • Smooth, Radiant & Clear",
        "Fenrir" to "Fenrir • Confident, Deep & Strong",
        "Puck" to "Puck • Playful, Dynamic & Cheerful",
        "Charon" to "Charon • Deep, Resonant & Thoughtful"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & API Key", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.CHAT) },
                        modifier = Modifier.testTag("settings_back_button")
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
            // 1. Google Gemini API Key Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = AlyaRuby)
                        Column {
                            Text(text = "User-Owned Gemini API Key", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = "Encrypted in Android KeyStore (AES-256 GCM). Never logged or exposed.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (hasKey) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = StateListeningColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = maskedKey,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteApiKey() },
                                    modifier = Modifier.testTag("delete_api_key_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Key",
                                        tint = StateErrorColor
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { viewModel.updateApiKeyInput(it) },
                        label = { Text(if (hasKey) "Update Gemini API Key" else "Enter Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        visualTransformation = if (showKeyText) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_text_field"),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showKeyText = !showKeyText }) {
                                Icon(
                                    imageVector = if (showKeyText) Icons.Default.Lock else Icons.Default.Key,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AlyaRuby,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveApiKey() },
                            enabled = apiKeyInput.isNotBlank() && !isValidating,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_api_key_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AlyaRuby)
                        ) {
                            Text("Save Key")
                        }

                        OutlinedButton(
                            onClick = { viewModel.validateStoredKey() },
                            enabled = (hasKey || apiKeyInput.isNotBlank()) && !isValidating,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("validate_api_key_button")
                        ) {
                            if (isValidating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Key")
                            }
                        }
                    }

                    if (validationMsg != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isKeyValid == true) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isKeyValid == true) StateListeningColor else StateErrorColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = validationMsg.orEmpty(),
                                fontSize = 12.sp,
                                color = if (isKeyValid == true) StateListeningColor else StateErrorColor
                            )
                        }
                    }
                }
            }

            // 2. Gemini Live Engine Settings
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = AlyaSapphire)
                        Column {
                            Text(text = "Live Conversation Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = "High-fidelity 16kHz voice pipeline with Gemini 2.0-Flash Live audio",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Language selection with Flags
                    Text(text = "Primary Language & Regional Dialect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    val currentLangObj = LanguageCatalog.findLanguage(selectedLanguage) ?: LanguageCatalog.languages.first()
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                languageSearchQuery = ""
                                showLanguageDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = currentLangObj.flag, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = currentLangObj.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(text = currentLangObj.country, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(imageVector = Icons.Default.Translate, contentDescription = "Change Language", tint = AlyaRuby)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Voice selection
                    Text(text = "Prebuilt Voice (TTS)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = voiceMenuExpanded,
                        onExpandedChange = { voiceMenuExpanded = !voiceMenuExpanded }
                    ) {
                        val currentVoiceLabel = voices.firstOrNull { it.first == selectedVoice }?.second ?: selectedVoice
                        OutlinedTextField(
                            value = currentVoiceLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AlyaRuby,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = voiceMenuExpanded,
                            onDismissRequest = { voiceMenuExpanded = false }
                        ) {
                            voices.forEach { (vKey, vLabel) ->
                                DropdownMenuItem(
                                    text = { Text(vLabel) },
                                    onClick = {
                                        viewModel.updateSelectedVoice(vKey)
                                        voiceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Subtitles toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Live Subtitles / Transcripts", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = "Display text captions during Live Voice conversation",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = subtitlesEnabled,
                            onCheckedChange = { viewModel.toggleSubtitles(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AlyaRuby, checkedTrackColor = AlyaRuby.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            // 3. Studio & Character Info Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Character & Developer Attribution", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "• Character: Alisa Mikhailovna Kujou (Alya)", fontSize = 13.sp)
                    Text(text = "• Age & Birthday: 18 (September 2008)", fontSize = 13.sp)
                    Text(text = "• Primary Spoken Language: Hindi (Conversational)", fontSize = 13.sp)
                    Text(text = "• Developer: Rohim Mandal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AlyaRuby)
                    Text(text = "• Studio: SBSM35G-Z STUDIO", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AlyaSapphire)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Real-time bidirectional Gemini Live Native Audio preview without third-party TTS.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showLanguageDialog) {
        val filteredLanguages = remember(languageSearchQuery) {
            if (languageSearchQuery.isBlank()) {
                LanguageCatalog.languages
            } else {
                LanguageCatalog.languages.filter {
                    it.name.contains(languageSearchQuery, ignoreCase = true) ||
                    it.country.contains(languageSearchQuery, ignoreCase = true)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text("Select Language with Flag", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = languageSearchQuery,
                        onValueChange = { languageSearchQuery = it },
                        placeholder = { Text("Search language or country...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (languageSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { languageSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredLanguages, key = { "${it.country}_${it.name}_${it.code}" }) { lang ->
                            val isSelected = lang.name.equals(selectedLanguage, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) AlyaRuby.copy(alpha = 0.15f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateSelectedLanguage(lang.name, lang.country)
                                        showLanguageDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = lang.flag, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = lang.name,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = if (isSelected) AlyaRuby else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = lang.country,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = AlyaRuby,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
