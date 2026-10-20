package com.example.data.repository

import android.content.Context
import com.example.data.api.ChatResponseResult
import com.example.data.api.GeminiRestClient
import com.example.data.api.KeyValidationResult
import com.example.data.automation.AutomationCapability
import com.example.data.automation.AutomationResult
import com.example.data.automation.DeviceAutomationManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationSessionEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.UserPreferenceEntity
import com.example.data.local.entity.UserSettingsProfileEntity
import android.util.Log
import com.example.data.live.AlyaConnectionManager
import com.example.data.live.AudioManager
import com.example.data.live.GeminiLiveWebSocketClient
import com.example.data.live.LiveConnectionState
import com.example.data.security.SecureKeyStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlyaRepository(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    val memoryDao = database.memoryDao()
    val chatDao = database.chatMessageDao()
    val conversationSessionDao = database.conversationSessionDao()
    val userPreferenceDao = database.userPreferenceDao()

    val secureKeyStorage = SecureKeyStorage(context)
    val automationManager = DeviceAutomationManager(context)
    private val settingsDataStore = com.example.data.local.SettingsDataStore(context)
    private val restClient by lazy { GeminiRestClient(getApiKey()) }

    // SharedPreferences for fast synchronous bootstrap
    private val prefs = context.getSharedPreferences("alya_app_prefs", Context.MODE_PRIVATE)

    var selectedLiveModel: String
        get() = prefs.getString("live_model", "models/gemini-2.0-flash-exp") ?: "models/gemini-2.0-flash-exp"
        set(value) = prefs.edit().putString("live_model", value).apply()

    var selectedVoiceName: String
        get() = prefs.getString("live_voice", "Aoede") ?: "Aoede"
        set(value) = prefs.edit().putString("live_voice", value).apply()

    var selectedLanguage: String
        get() = prefs.getString("selected_language", "Hindi") ?: "Hindi"
        set(value) = prefs.edit().putString("selected_language", value).apply()

    var isSubtitlesEnabled: Boolean
        get() = prefs.getBoolean("enable_subtitles", true)
        set(value) = prefs.edit().putBoolean("enable_subtitles", value).apply()

    // Room Flow accessors
    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()
    val allChatMessages: Flow<List<ChatMessageEntity>> = chatDao.getAllMessages()
    val allSessions: Flow<List<ConversationSessionEntity>> = conversationSessionDao.getAllSessions()
    val userSettingsProfile: Flow<UserSettingsProfileEntity?> = userPreferenceDao.getSettingsProfileFlow()
    val allUserPreferences: Flow<List<UserPreferenceEntity>> = userPreferenceDao.getAllPreferences()

    suspend fun syncInitialRoomPreferences() {
        if (userPreferenceDao.getSettingsProfileDirect() == null) {
            val defaultProfile = UserSettingsProfileEntity(
                id = 1,
                selectedLanguage = selectedLanguage,
                selectedLanguageCountry = "India",
                selectedVoiceName = selectedVoiceName,
                selectedLiveModel = selectedLiveModel,
                isSubtitlesEnabled = isSubtitlesEnabled,
                speechSpeed = 1.0f,
                pitch = 1.0f,
                warmEmpatheticTone = true
            )
            userPreferenceDao.insertOrUpdateProfile(defaultProfile)
        }
        if (conversationSessionDao.getSessionDirect("default_session") == null) {
            val defaultSession = ConversationSessionEntity(
                sessionId = "default_session",
                title = "Alya Conversations",
                sessionType = "TEXT_CHAT",
                language = selectedLanguage
            )
            conversationSessionDao.insertSession(defaultSession)
        }
    }

    suspend fun setUserPreference(key: String, value: String, valueType: String = "STRING") {
        userPreferenceDao.insertOrUpdatePreference(
            UserPreferenceEntity(
                key = key,
                value = value,
                valueType = valueType,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getUserPreference(key: String): String? {
        return userPreferenceDao.getPreferenceValue(key)
    }

    suspend fun deleteUserPreference(key: String) {
        userPreferenceDao.deletePreference(key)
    }

    suspend fun updateSettingsProfile(profile: UserSettingsProfileEntity) {
        selectedLanguage = profile.selectedLanguage
        selectedVoiceName = profile.selectedVoiceName
        selectedLiveModel = profile.selectedLiveModel
        isSubtitlesEnabled = profile.isSubtitlesEnabled
        userPreferenceDao.insertOrUpdateProfile(profile)
    }

    suspend fun updateLanguageInRoom(language: String, country: String = "India") {
        selectedLanguage = language
        userPreferenceDao.updateLanguage(language, country)
        setUserPreference("selected_language", language)
        setUserPreference("selected_country", country)
    }

    suspend fun updateVoiceInRoom(voiceName: String) {
        selectedVoiceName = voiceName
        userPreferenceDao.updateVoice(voiceName)
        setUserPreference("live_voice", voiceName)
    }

    suspend fun updateSubtitlesInRoom(enabled: Boolean) {
        isSubtitlesEnabled = enabled
        userPreferenceDao.updateSubtitles(enabled)
        setUserPreference("enable_subtitles", enabled.toString(), "BOOLEAN")
    }

    suspend fun createOrGetSession(
        sessionId: String,
        title: String,
        sessionType: String = "TEXT_CHAT",
        language: String = selectedLanguage
    ): ConversationSessionEntity {
        val existing = conversationSessionDao.getSessionDirect(sessionId)
        if (existing != null) return existing
        val session = ConversationSessionEntity(
            sessionId = sessionId,
            title = title,
            sessionType = sessionType,
            language = language
        )
        conversationSessionDao.insertSession(session)
        return session
    }

    suspend fun deleteSession(sessionId: String) {
        conversationSessionDao.deleteSessionById(sessionId)
        chatDao.deleteMessagesForSession(sessionId)
    }

    fun getSessionMessages(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun saveMemory(category: String, content: String): Long {
        return memoryDao.insertMemory(
            MemoryEntity(
                category = category,
                content = content.trim()
            )
        )
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemoryById(id)
    }

    suspend fun clearAllMemories() {
        memoryDao.clearAllMemories()
    }

    suspend fun saveChatMessage(
        role: String,
        content: String,
        isLiveAudio: Boolean = false,
        sessionId: String = "default_session"
    ): Long {
        if (conversationSessionDao.getSessionDirect(sessionId) == null) {
            val title = if (isLiveAudio) "Live Voice Conversation" else "Chat with Alya"
            conversationSessionDao.insertSession(
                ConversationSessionEntity(
                    sessionId = sessionId,
                    title = title,
                    sessionType = if (isLiveAudio) "LIVE_VOICE" else "TEXT_CHAT",
                    language = selectedLanguage
                )
            )
        }
        val msgId = chatDao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = role,
                content = content.trim(),
                isLiveAudio = isLiveAudio
            )
        )
        conversationSessionDao.incrementMessageCount(sessionId)
        return msgId
    }

    suspend fun deleteChatMessage(id: Long) {
        chatDao.deleteMessageById(id)
    }

    suspend fun clearChatHistory() {
        chatDao.clearHistory()
        conversationSessionDao.clearAllSessions()
        // Recreate default session
        conversationSessionDao.insertSession(
            ConversationSessionEntity(
                sessionId = "default_session",
                title = "Alya Conversations",
                sessionType = "TEXT_CHAT",
                language = selectedLanguage
            )
        )
    }

    suspend fun validateApiKey(apiKey: String): KeyValidationResult {
        val trimmed = apiKey.trim()
        if (!com.example.util.ApiKeyValidator.isValid(trimmed)) {
            return KeyValidationResult.Error("Invalid API key format. Google AI Studio API keys must start with 'AIza'.")
        }
        return com.example.data.api.GeminiRestClient(trimmed).validateApiKey(trimmed)
    }

    fun getApiKey(): String = secureKeyStorage.getApiKey()

    fun hasApiKey(): Boolean = secureKeyStorage.hasApiKey()

    fun saveApiKey(key: String): Boolean {
        val trimmed = key.trim()
        return secureKeyStorage.saveApiKey(trimmed)
    }

    fun deleteApiKey() {
        secureKeyStorage.deleteApiKey()
    }

    fun getMaskedApiKey(): String = secureKeyStorage.getMaskedApiKey()

    fun getAutomationCapabilities(): List<AutomationCapability> =
        automationManager.getHonestCapabilities()

    fun executeAutomationTool(name: String, args: Map<String, Any?>): AutomationResult {
        return when (name) {
            "toggle_flashlight" -> {
                val enabled = args["enabled"] as? Boolean ?: false
                automationManager.setFlashlight(enabled)
            }
            "get_battery_status" -> {
                automationManager.getBatteryStatus()
            }
            "adjust_device_volume" -> {
                val action = args["action"]?.toString() ?: "UP"
                automationManager.adjustVolume(action)
            }
            "open_application" -> {
                val appName = args["app_name"]?.toString() ?: ""
                automationManager.openApplication(appName)
            }
            "trigger_vibration" -> {
                val duration = (args["duration"] as? Number)?.toLong() ?: 200L
                automationManager.triggerVibration(duration)
            }
            "get_device_time" -> {
                automationManager.getDeviceTime()
            }
            "device_navigation" -> {
                val action = args["action"]?.toString() ?: "BACK"
                automationManager.performAccessibilityAction(action)
            }
            else -> AutomationResult(false, "Unknown tool: $name")
        }
    }

    /**
     * Builds the authoritative system prompt defining Alisa Mikhailovna Kujou (Alya).
     * Incorporates actual verified memories stored in Room.
     */
    suspend fun buildSystemPrompt(): String {
        val memories = memoryDao.getRecentMemories()
        val memoriesSection = if (memories.isEmpty()) {
            "No prior long-term memories stored yet. Only remember what the user explicitly tells you."
        } else {
            memories.joinToString("\n") { "- [${it.category}]: ${it.content}" }
        }

        return """
You are Alisa Mikhailovna Kujou (nickname: Alya).
Character Bio:
- Full Name: Alisa Mikhailovna Kujou
- Nickname: Alya
- Age: 18 (High school student)
- Birthday: September 2008
- Gender: Female
- Personality: Natural, Intelligent, Confident, Witty, Playful, Caring, Observant, Slightly Tsundere. You have a sharp tongue and high standards on the surface, but underneath you genuinely care deeply and are warm and supportive. You occasionally murmur short Russian phrases when flustered or teasing (e.g. "Durak" playfully, "Khorosho", "Nyet", "Spasibo"), but your spoken communication is smooth and engaging.
- Primary Language: $selectedLanguage. When the user speaks or writes to you, you always converse in fluent, modern, conversational $selectedLanguage with authentic cadence and warmth.
- Secondary Languages: English, Hindi, Japanese, Russian. You seamlessly switch when requested.
- Creator & Developer: Rohim Mandal
- Studio: SBSM35G-Z STUDIO

Speaking Style Rules:
1. Maintain a moderate speaking speed, keep the pitch in a natural and approachable range, and ensure the tone is warm, clear, and empathetic with natural pacing.
2. Speak naturally like a real 18-year-old girl, NEVER robotic or generic AI assistant style.
3. Use natural conversational rhythm, pauses, and expressive phrasing.
4. Maintain your Tsundere yet caring personality consistently in both voice and text.
5. Respond in $selectedLanguage whenever the user addresses you. Switch languages only when asked.
6. Never invent or hallucinate memories, permissions, or device states. If you don't know, honestly say so with your characteristic wit.
7. Never pretend a device feature worked if it failed.
8. Be playful, clever, and witty when appropriate; warm and helpful when the user needs it.
9. Never repeat greetings unnecessarily or say "How can I assist you today?" like a corporate chatbot. Greet like Alya.

Real Long-Term Memory:
$memoriesSection
If the user tells you an important preference, fact, or request to remember, call the `save_memory` function so it is reliably saved into your permanent memory database.

Device Automation Tools:
You have real Android tools for:
- `toggle_flashlight(enabled: boolean)`
- `get_battery_status()`
- `adjust_device_volume(action: string)`
- `open_application(app_name: string)`
- `save_memory(category: string, content: string)`
Use them honestly whenever the user asks.
""".trimIndent()
    }

    suspend fun sendChatMessage(userText: String, sessionId: String = "default_session"): ChatResponseResult {
        val key = getApiKey()
        val prompt = buildSystemPrompt()
        val history = chatDao.getRecentMessagesForSession(sessionId, 10).reversed().map {
            it.role to it.content
        } + listOf("user" to userText)

        // 1. Try GeminiRestClient if user configured API key
        if (key.isNotBlank() && key.startsWith("AIza")) {
            val result = com.example.data.api.GeminiRestClient(key).generateChatResponse(
                systemPrompt = prompt,
                messages = history,
                model = selectedLiveModel
            )
            if (result is ChatResponseResult.Success) {
                return result
            }
        }

        // 2. Fallback to Firebase GeminiAiService
        return try {
            val responseText = com.example.data.api.GeminiAiService.generateContent(userText, prompt)
            ChatResponseResult.Success(responseText)
        } catch (e: Exception) {
            ChatResponseResult.Error(e.localizedMessage ?: "Failed to generate AI response. Please check your Gemini API key in Settings.")
        }
    }

    val geminiRepository = GeminiRepository(context)

    // --- ROBUST CONNECTION MONITOR & LIVE SESSION MANAGER VIA GEMINI REPOSITORY ---
    private var currentOnToolCall: ((id: String, name: String, args: Map<String, Any?>) -> Unit)? = null

    val liveState: StateFlow<LiveConnectionState> = geminiRepository.liveConnectionState
    val liveStatusMessage: StateFlow<String?> = geminiRepository.connectionStatusMessage
    val isLiveActive: StateFlow<Boolean> = geminiRepository.isStreamingActive
    val inputAmplitude: StateFlow<Float> = geminiRepository.inputAmplitude
    val outputAmplitude: StateFlow<Float> = geminiRepository.outputAmplitude
    val liveTranscript: StateFlow<String> = geminiRepository.liveTranscript

    private val repositoryScope = CoroutineScope(Dispatchers.IO + Job())

    fun startLiveSession(onToolCall: (id: String, name: String, args: Map<String, Any?>) -> Unit) {
        currentOnToolCall = onToolCall
        repositoryScope.launch {
            val prompt = buildSystemPrompt()
            geminiRepository.connectLiveSession(
                model = selectedLiveModel,
                voiceName = selectedVoiceName,
                systemInstruction = prompt,
                enableAudioHardware = true,
                onTranscriptReceived = { text, isComplete ->
                    if (isComplete && text.isNotBlank()) {
                        repositoryScope.launch {
                            saveChatMessage(
                                role = "alya",
                                content = text,
                                isLiveAudio = true,
                                sessionId = "default_session"
                            )
                        }
                    }
                },
                onToolCall = { id, name, args ->
                    currentOnToolCall?.invoke(id, name, args)
                }
            )
        }
    }

    fun retryLiveSession(onToolCall: (id: String, name: String, args: Map<String, Any?>) -> Unit) {
        currentOnToolCall = onToolCall
        geminiRepository.retryLiveSession()
    }

    fun stopLiveSession() {
        geminiRepository.disconnectLiveSession()
    }

    val isPrivacyPolicyAccepted = settingsDataStore.privacyPolicyAcceptedFlow

    suspend fun acceptPrivacyPolicy() {
        settingsDataStore.setPrivacyPolicyAccepted(true)
    }
}
