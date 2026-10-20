package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.ChatResponseResult
import com.example.data.api.KeyValidationResult
import com.example.data.automation.AutomationCapability
import com.example.data.automation.AutomationResult
import com.example.data.live.AudioManager
import com.example.data.live.GeminiLiveWebSocketClient
import com.example.data.live.LiveConnectionState
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationSessionEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.UserPreferenceEntity
import com.example.data.local.entity.UserSettingsProfileEntity
import com.example.data.repository.AlyaRepository
import com.example.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlyaViewModel(application: Application) : AndroidViewModel(application) {
    val repository = AlyaRepository(application)

    // Navigation
    private val _currentScreen = MutableStateFlow(Screen.CHAT)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // API Key State
    private val _apiKeyInput = MutableStateFlow("")
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _maskedApiKey = MutableStateFlow(repository.getMaskedApiKey())
    val maskedApiKey: StateFlow<String> = _maskedApiKey.asStateFlow()

    private val _hasApiKey = MutableStateFlow(repository.hasApiKey())
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val _isValidatingKey = MutableStateFlow(false)
    val isValidatingKey: StateFlow<Boolean> = _isValidatingKey.asStateFlow()

    private val _keyValidationMessage = MutableStateFlow<String?>(null)
    val keyValidationMessage: StateFlow<String?> = _keyValidationMessage.asStateFlow()

    private val _isKeyValid = MutableStateFlow<Boolean?>(null)
    val isKeyValid: StateFlow<Boolean?> = _isKeyValid.asStateFlow()

    // Settings
    private val _selectedModel = MutableStateFlow(repository.selectedLiveModel)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _selectedVoice = MutableStateFlow(repository.selectedVoiceName)
    val selectedVoice: StateFlow<String> = _selectedVoice.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(repository.selectedLanguage)
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _subtitlesEnabled = MutableStateFlow(repository.isSubtitlesEnabled)
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()

    val isPrivacyPolicyAccepted: StateFlow<Boolean> = repository.isPrivacyPolicyAccepted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Room Persistent User Preferences & Conversation Sessions
    val userSettingsProfile: StateFlow<UserSettingsProfileEntity?> = repository.userSettingsProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val conversationSessions: StateFlow<List<ConversationSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUserPreferences: StateFlow<List<UserPreferenceEntity>> = repository.allUserPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow("default_session")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    // Chat Interface
    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.allChatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _textInput = MutableStateFlow("")
    val textInput: StateFlow<String> = _textInput.asStateFlow()

    private val _isSendingChat = MutableStateFlow(false)
    val isSendingChat: StateFlow<Boolean> = _isSendingChat.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    // Long-Term Memory
    val memories: StateFlow<List<MemoryEntity>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Device Automation
    private val _capabilities = MutableStateFlow<List<AutomationCapability>>(emptyList())
    val capabilities: StateFlow<List<AutomationCapability>> = _capabilities.asStateFlow()

    private val _lastAutomationResult = MutableStateFlow<AutomationResult?>(null)
    val lastAutomationResult: StateFlow<AutomationResult?> = _lastAutomationResult.asStateFlow()

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    // Real-Time Gemini Live Audio Pipeline (Delegated to Repository Connection Monitor StateFlow)
    val liveState: StateFlow<LiveConnectionState> = repository.liveState
    val liveStatusMessage: StateFlow<String?> = repository.liveStatusMessage
    val isLiveActive: StateFlow<Boolean> = repository.isLiveActive

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    val inputAmplitude: StateFlow<Float> = repository.inputAmplitude
    val outputAmplitude: StateFlow<Float> = repository.outputAmplitude
    val liveTranscript: StateFlow<String> = repository.liveTranscript

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private var audioManager: AudioManager? = null
    private var liveWebSocketClient: GeminiLiveWebSocketClient? = null

    init {
        refreshCapabilities()
        viewModelScope.launch {
            repository.syncInitialRoomPreferences()
            validateStoredKey()
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun setAudioPermissionGranted(granted: Boolean) {
        _hasAudioPermission.value = granted
    }

    fun updateApiKeyInput(key: String) {
        _apiKeyInput.value = key
        _keyValidationMessage.value = null
        _isKeyValid.value = null
    }

    fun saveApiKey() {
        val key = _apiKeyInput.value.trim()
        if (key.isNotEmpty()) {
            if (!com.example.util.ApiKeyValidator.isValid(key)) {
                _isKeyValid.value = false
                _keyValidationMessage.value = "Invalid API key format. API keys must start with the prefix 'AIza' (A-I-z-a)."
                return
            }
            repository.saveApiKey(key)
            _hasApiKey.value = true
            _maskedApiKey.value = repository.getMaskedApiKey()
            _apiKeyInput.value = ""
            validateKey(key)
        }
    }

    fun deleteApiKey() {
        repository.deleteApiKey()
        _hasApiKey.value = false
        _maskedApiKey.value = ""
        _apiKeyInput.value = ""
        _isKeyValid.value = null
        _keyValidationMessage.value = "API key removed."
    }

    fun validateStoredKey() {
        val key = repository.getApiKey()
        if (key.isBlank()) return // Silently skip validation if no key configured
        
        if (!com.example.util.ApiKeyValidator.isValid(key)) {
            _isKeyValid.value = false
            _keyValidationMessage.value = "Invalid or missing API key format. Keys must start with the prefix 'AIza' (A-I-z-a)."
            return
        }
        validateKey(key)
    }

    private fun validateKey(key: String) {
        val trimmed = key.trim()
        if (!com.example.util.ApiKeyValidator.isValid(trimmed)) {
            _isKeyValid.value = false
            _keyValidationMessage.value = "Invalid API key format. API keys must start with the prefix 'AIza' (A-I-z-a)."
            return
        }
        viewModelScope.launch {
            _isValidatingKey.value = true
            _keyValidationMessage.value = "Validating key with Google Gemini API..."
            when (val result = repository.validateApiKey(trimmed)) {
                is KeyValidationResult.Success -> {
                    _isKeyValid.value = true
                    _keyValidationMessage.value = "API key verified successfully!"
                }
                is KeyValidationResult.Error -> {
                    _isKeyValid.value = false
                    _keyValidationMessage.value = result.message
                }
            }
            _isValidatingKey.value = false
        }
    }

    fun updateSelectedModel(model: String) {
        repository.selectedLiveModel = model
        _selectedModel.value = model
        viewModelScope.launch {
            repository.setUserPreference("live_model", model)
        }
    }

    fun updateSelectedVoice(voice: String) {
        _selectedVoice.value = voice
        viewModelScope.launch {
            repository.updateVoiceInRoom(voice)
        }
    }

    fun updateSelectedLanguage(language: String, country: String = "India") {
        _selectedLanguage.value = language
        viewModelScope.launch {
            repository.updateLanguageInRoom(language, country)
        }
    }

    fun toggleSubtitles(enabled: Boolean) {
        _subtitlesEnabled.value = enabled
        viewModelScope.launch {
            repository.updateSubtitlesInRoom(enabled)
        }
    }

    fun switchSession(sessionId: String) {
        _currentSessionId.value = sessionId
    }

    fun createNewSession(title: String, sessionType: String = "TEXT_CHAT") {
        val newSessionId = "session_${System.currentTimeMillis()}"
        viewModelScope.launch {
            repository.createOrGetSession(newSessionId, title, sessionType)
            _currentSessionId.value = newSessionId
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = "default_session"
            }
        }
    }

    fun updateTextInput(text: String) {
        _textInput.value = text
    }

    fun sendTextMessage() {
        val query = _textInput.value.trim()
        if (query.isEmpty() || _isSendingChat.value) return

        viewModelScope.launch {
            _isSendingChat.value = true
            _chatError.value = null
            _textInput.value = ""

            val sessionId = _currentSessionId.value
            // Save user message to Room with session association
            repository.saveChatMessage(role = "user", content = query, sessionId = sessionId)

            // Send to Gemini
            when (val result = repository.sendChatMessage(query, sessionId)) {
                is ChatResponseResult.Success -> {
                    repository.saveChatMessage(role = "alya", content = result.text, sessionId = sessionId)
                }
                is ChatResponseResult.Error -> {
                    _chatError.value = result.message
                    repository.saveChatMessage(
                        role = "system",
                        content = "Error: ${result.message}",
                        sessionId = sessionId
                    )
                }
            }
            _isSendingChat.value = false
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChatHistory()
        }
    }

    fun addManualMemory(category: String, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            repository.saveMemory(category, content)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
        }
    }

    fun refreshCapabilities() {
        _capabilities.value = repository.getAutomationCapabilities()
    }

    fun triggerAutomation(name: String, args: Map<String, Any?> = emptyMap()) {
        val result = repository.executeAutomationTool(name, args)
        _lastAutomationResult.value = result
        if (name == "toggle_flashlight" && result.success) {
            _isFlashlightOn.value = args["enabled"] as? Boolean ?: !_isFlashlightOn.value
        }
        refreshCapabilities()
    }

    // --- GEMINI LIVE REAL-TIME AUDIO PIPELINE (DELEGATED TO REPOSITORY CONNECTION MONITOR) ---

    fun startLiveSession() {
        val key = repository.getApiKey()
        if (key.isBlank()) {
            navigateTo(Screen.SETTINGS)
            return
        }

        if (!_hasAudioPermission.value) {
            navigateTo(Screen.PERMISSIONS)
            return
        }

        repository.startLiveSession { id, name, args ->
            handleLiveToolCall(id, name, args)
        }
    }

    fun retryLiveSession() {
        repository.retryLiveSession { id, name, args ->
            handleLiveToolCall(id, name, args)
        }
    }

    private fun handleLiveToolCall(id: String, name: String, args: Map<String, Any?>) {
        viewModelScope.launch {
            if (name == "save_memory") {
                val category = args["category"]?.toString() ?: "FACT"
                val content = args["content"]?.toString() ?: ""
                if (content.isNotBlank()) {
                    repository.saveMemory(category, content)
                }
            } else {
                val result = repository.executeAutomationTool(name, args)
                if (name == "toggle_flashlight" && result.success) {
                    _isFlashlightOn.value = args["enabled"] as? Boolean ?: false
                }
            }
        }
    }

    fun interruptLiveAudio() {
        // Handled via repository audio manager / barge-in
    }

    fun toggleMicMute() {
        _isMicMuted.value = !_isMicMuted.value
    }

    fun acceptPrivacyPolicy() {
        viewModelScope.launch {
            repository.acceptPrivacyPolicy()
        }
    }

    fun stopLiveSession() {
        repository.stopLiveSession()
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveSession()
    }
}
