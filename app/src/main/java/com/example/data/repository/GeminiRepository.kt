package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.live.AudioManager
import com.example.data.live.GeminiLiveWebSocketClient
import com.example.data.live.LiveConnectionState
import com.example.data.security.SecureKeyStorage
import com.example.util.ApiKeyValidator
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * GeminiRepository manages initialization of the Google Generative AI SDK
 * and integrates the Multimodal Live API (BidiGenerateContent), ensuring a persistent
 * streaming connection for natural, real-time voice interactions.
 */
class GeminiRepository(private val context: Context? = null) {

    companion object {
        private const val TAG = "GeminiRepository"
        const val DEFAULT_LIVE_MODEL = "models/gemini-2.0-flash-exp"
        const val DEFAULT_VOICE_NAME = "Aoede"
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO + Job())

    // --- StateFlow and SharedFlow for Real-time Multimodal Live Streaming ---
    private val _liveConnectionState = MutableStateFlow(LiveConnectionState.IDLE)
    val liveConnectionState: StateFlow<LiveConnectionState> = _liveConnectionState.asStateFlow()

    private val _connectionStatusMessage = MutableStateFlow<String?>(null)
    val connectionStatusMessage: StateFlow<String?> = _connectionStatusMessage.asStateFlow()

    private val _isStreamingActive = MutableStateFlow(false)
    val isStreamingActive: StateFlow<Boolean> = _isStreamingActive.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude: StateFlow<Float> = _inputAmplitude.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    private val _incomingAudioChunks = MutableSharedFlow<Pair<ByteArray, Int>>(extraBufferCapacity = 64)
    val incomingAudioChunks: SharedFlow<Pair<ByteArray, Int>> = _incomingAudioChunks.asSharedFlow()

    private val _streamingTranscripts = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 64)
    val streamingTranscripts: SharedFlow<Pair<String, Boolean>> = _streamingTranscripts.asSharedFlow()

    // Internal components for persistent streaming
    private var webSocketClient: GeminiLiveWebSocketClient? = null
    private var audioManager: AudioManager? = null
    private val isManuallyClosed = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)

    private var activeModel: String = DEFAULT_LIVE_MODEL
    private var activeVoice: String = DEFAULT_VOICE_NAME
    private var activeSystemPrompt: String = ""
    private var activeToolHandler: ((id: String, name: String, args: Map<String, Any?>) -> Unit)? = null
    private var activeAudioCallback: ((ByteArray, Int) -> Unit)? = null
    private var activeTranscriptCallback: ((String, Boolean) -> Unit)? = null
    private var activeInterruptedCallback: (() -> Unit)? = null

    /**
     * Resolves the Gemini API key securely:
     * 1. Checks EncryptedSharedPreferences (SecureKeyStorage) if context is available.
     * 2. Checks BuildConfig.GEMINI_API_KEY via safe reflection.
     */
    fun resolveApiKey(): String {
        // 1. Try SecureKeyStorage if context is available
        context?.let { ctx ->
            try {
                val stored = SecureKeyStorage(ctx).getApiKey()
                if (stored.isNotBlank() && ApiKeyValidator.isValid(stored)) {
                    return stored
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read key from SecureKeyStorage: ${e.message}")
            }
        }

        // 2. Try BuildConfig safely
        try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            val buildKey = field.get(null) as? String
            if (!buildKey.isNullOrBlank() && buildKey != "MY_GEMINI_API_KEY") {
                return buildKey.trim()
            }
        } catch (_: Exception) {}

        return ""
    }

    /**
     * Verifies if a valid Gemini API key is configured.
     */
    fun hasValidApiKey(): Boolean {
        val key = resolveApiKey()
        return key.isNotBlank() && ApiKeyValidator.isValid(key)
    }

    // =========================================================================
    // MULTIMODAL LIVE API: PERSISTENT STREAMING CONNECTION
    // =========================================================================

    /**
     * Connects to the Gemini Multimodal Live API (BidiGenerateContent) establishing
     * a persistent bidirectional WebSocket streaming connection.
     *
     * @param model Live model identifier (default: models/gemini-2.0-flash-exp)
     * @param voiceName Gemini natural voice: Aoede, Kore, Ursa, Cinder, Fenrir, Puck, Charon
     * @param systemInstruction System personality instructions (e.g., Alya character prompt)
     * @param enableAudioHardware If true and context is present, activates hardware 16kHz capture and playback
     * @param onAudioReceived Optional listener for incoming audio chunks (PCM bytes, sample rate)
     * @param onTranscriptReceived Optional listener for streaming transcripts (text, isComplete)
     * @param onInterrupted Optional callback when user interrupts model speech (barge-in)
     * @param onToolCall Optional listener for device automation and memory function calls
     */
    fun connectLiveSession(
        model: String = DEFAULT_LIVE_MODEL,
        voiceName: String = DEFAULT_VOICE_NAME,
        systemInstruction: String = "",
        enableAudioHardware: Boolean = true,
        onAudioReceived: ((ByteArray, Int) -> Unit)? = null,
        onTranscriptReceived: ((String, Boolean) -> Unit)? = null,
        onInterrupted: (() -> Unit)? = null,
        onToolCall: ((id: String, name: String, args: Map<String, Any?>) -> Unit)? = null
    ) {
        val apiKey = resolveApiKey()
        if (apiKey.isBlank() || !ApiKeyValidator.isValid(apiKey)) {
            _liveConnectionState.value = LiveConnectionState.ERROR
            _connectionStatusMessage.value = "Gemini API key is missing or invalid. Please configure a valid key starting with 'AIza' in Settings."
            return
        }

        activeModel = model
        activeVoice = voiceName
        activeSystemPrompt = systemInstruction
        activeToolHandler = onToolCall
        activeAudioCallback = onAudioReceived
        activeTranscriptCallback = onTranscriptReceived
        activeInterruptedCallback = onInterrupted

        isManuallyClosed.set(false)
        reconnectAttempts.set(0)
        cleanupLiveResources()

        _isStreamingActive.value = true
        _liveConnectionState.value = LiveConnectionState.CONNECTING
        _connectionStatusMessage.value = "Establishing persistent connection to Gemini Live..."
        _liveTranscript.value = ""

        repositoryScope.launch {
            try {
                // Initialize audio hardware if enabled and context is available
                if (enableAudioHardware && context != null) {
                    audioManager = AudioManager(
                        context = context,
                        onAudioChunkRecorded = { chunk ->
                            if (_isStreamingActive.value) {
                                webSocketClient?.sendAudioChunk(chunk)
                            }
                        },
                        onAmplitudeChanged = { inRms, outRms ->
                            _inputAmplitude.value = inRms
                            _outputAmplitude.value = outRms
                        },
                        onSpeakingStateChanged = { isSpeaking ->
                            if (_isStreamingActive.value) {
                                if (isSpeaking) {
                                    _liveConnectionState.value = LiveConnectionState.SPEAKING
                                } else if (_liveConnectionState.value == LiveConnectionState.SPEAKING) {
                                    _liveConnectionState.value = LiveConnectionState.LISTENING
                                }
                            }
                        }
                    )
                    audioManager?.startPlayback()
                    val recordStarted = audioManager?.startRecording() ?: false
                    if (!recordStarted) {
                        Log.w(TAG, "AudioRecord could not start immediately; proceeding with network stream")
                    }
                }

                initWebSocketClient(apiKey)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect live session: ${e.message}", e)
                handlePersistentReconnect("Failed to start live session: ${e.localizedMessage}")
            }
        }
    }

    private fun initWebSocketClient(apiKey: String) {
        webSocketClient = GeminiLiveWebSocketClient(
            apiKey = apiKey,
            model = activeModel,
            voiceName = activeVoice,
            systemPrompt = activeSystemPrompt,
            onStateChanged = { state, msg ->
                handleLiveStateChange(state, msg)
            },
            onAudioDataReceived = { audioBytes, sampleRate ->
                _incomingAudioChunks.tryEmit(audioBytes to sampleRate)
                activeAudioCallback?.invoke(audioBytes, sampleRate)
                audioManager?.enqueueAudio(audioBytes, sampleRate)
            },
            onTranscriptReceived = { text, isComplete ->
                _liveTranscript.value = text
                _streamingTranscripts.tryEmit(text to isComplete)
                activeTranscriptCallback?.invoke(text, isComplete)
            },
            onInterrupted = {
                audioManager?.bargeIn()
                _liveConnectionState.value = LiveConnectionState.INTERRUPTED
                activeInterruptedCallback?.invoke()
            },
            onToolCallReceived = { id, name, args ->
                activeToolHandler?.invoke(id, name, args)
            }
        )

        webSocketClient?.connect()
    }

    private fun handleLiveStateChange(state: LiveConnectionState, message: String?) {
        if (isManuallyClosed.get()) return

        when (state) {
            LiveConnectionState.CONNECTED -> {
                reconnectAttempts.set(0)
                _liveConnectionState.value = LiveConnectionState.CONNECTED
                _connectionStatusMessage.value = message ?: "Connected to Gemini Live"
                repositoryScope.launch {
                    delay(300)
                    if (_liveConnectionState.value == LiveConnectionState.CONNECTED) {
                        _liveConnectionState.value = LiveConnectionState.LISTENING
                    }
                }
            }
            LiveConnectionState.ERROR, LiveConnectionState.DISCONNECTED -> {
                handlePersistentReconnect(message ?: "Streaming connection lost")
            }
            else -> {
                _liveConnectionState.value = state
                if (message != null) {
                    _connectionStatusMessage.value = message
                }
            }
        }
    }

    /**
     * Implements exponential backoff to ensure persistent connection across network drops.
     */
    private fun handlePersistentReconnect(reason: String) {
        if (isManuallyClosed.get() || !_isStreamingActive.value) return

        val attempt = reconnectAttempts.incrementAndGet()
        if (attempt <= MAX_RECONNECT_ATTEMPTS) {
            val backoffMillis = (1000L * (1 shl (attempt - 1))).coerceAtMost(16000L)
            _liveConnectionState.value = LiveConnectionState.RECONNECTING
            _connectionStatusMessage.value = "Connection dropped. Auto-reconnecting ($attempt/$MAX_RECONNECT_ATTEMPTS) in ${backoffMillis / 1000}s..."
            Log.w(TAG, "Reconnecting persistent live stream ($attempt/$MAX_RECONNECT_ATTEMPTS) in ${backoffMillis}ms due to: $reason")

            repositoryScope.launch {
                delay(backoffMillis)
                if (!isManuallyClosed.get() && _isStreamingActive.value) {
                    val key = resolveApiKey()
                    if (key.isNotBlank()) {
                        initWebSocketClient(key)
                    }
                }
            }
        } else {
            _liveConnectionState.value = LiveConnectionState.ERROR
            _connectionStatusMessage.value = "Persistent connection failed after $MAX_RECONNECT_ATTEMPTS attempts. ($reason)"
            Log.e(TAG, "Max live streaming reconnection attempts reached.")
        }
    }

    /**
     * Streams raw 16kHz PCM audio chunk to the Gemini Multimodal Live API.
     */
    fun sendAudioChunk(pcmChunk: ByteArray) {
        webSocketClient?.sendAudioChunk(pcmChunk)
    }

    /**
     * Sends a text message into the persistent live conversation turn.
     */
    fun sendTextMessage(text: String) {
        webSocketClient?.sendTextMessage(text)
    }

    /**
     * Sends tool response back to Gemini Live API.
     */
    fun sendToolResponse(callId: String, result: Map<String, Any?>) {
        webSocketClient?.sendToolResponse(callId, result)
    }

    /**
     * Instant Barge-in: cancels current model audio output and restores listening state.
     */
    fun interrupt() {
        audioManager?.bargeIn()
        _liveConnectionState.value = LiveConnectionState.INTERRUPTED
        repositoryScope.launch {
            delay(150)
            if (_isStreamingActive.value) {
                _liveConnectionState.value = LiveConnectionState.LISTENING
            }
        }
    }

    /**
     * Manually triggers a session retry.
     */
    fun retryLiveSession() {
        reconnectAttempts.set(0)
        connectLiveSession(
            model = activeModel,
            voiceName = activeVoice,
            systemInstruction = activeSystemPrompt,
            enableAudioHardware = audioManager != null,
            onAudioReceived = activeAudioCallback,
            onTranscriptReceived = activeTranscriptCallback,
            onInterrupted = activeInterruptedCallback,
            onToolCall = activeToolHandler
        )
    }

    /**
     * Gracefully disconnects the persistent streaming session and releases hardware.
     */
    fun disconnectLiveSession() {
        isManuallyClosed.set(true)
        _isStreamingActive.value = false
        _liveConnectionState.value = LiveConnectionState.DISCONNECTED
        _connectionStatusMessage.value = "Disconnected"
        cleanupLiveResources()
    }

    private fun cleanupLiveResources() {
        try {
            webSocketClient?.disconnect()
            webSocketClient = null
            audioManager?.stopRecording()
            audioManager?.stopPlayback()
            audioManager?.release()
            audioManager = null
            _inputAmplitude.value = 0f
            _outputAmplitude.value = 0f
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up live resources: ${e.message}", e)
        }
    }

    // =========================================================================
    // STANDARD GENERATIVE AI SDK: TEXT & STREAMING GENERATION
    // =========================================================================

    /**
     * Generates a standard one-shot text response using the Google Generative AI SDK.
     */
    suspend fun generateResponse(prompt: String, modelName: String = "gemini-1.5-flash"): String {
        return withContext(Dispatchers.IO) {
            val key = resolveApiKey()
            if (key.isBlank()) {
                return@withContext "Error: Gemini API key is not configured. Please add your key in Settings."
            }
            try {
                val model = GenerativeModel(
                    modelName = modelName,
                    apiKey = key
                )
                val response = model.generateContent(prompt)
                response.text ?: "No response from Gemini."
            } catch (e: Exception) {
                "Error: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    /**
     * Generates a streaming text response using the Google Generative AI SDK.
     */
    fun generateStreamingResponse(prompt: String, modelName: String = "gemini-1.5-flash"): Flow<String> = flow {
        val key = resolveApiKey()
        if (key.isBlank()) {
            emit("Error: Gemini API key is not configured.")
            return@flow
        }
        val model = GenerativeModel(
            modelName = modelName,
            apiKey = key
        )
        model.generateContentStream(prompt).collect { chunk ->
            chunk.text?.let { emit(it) }
        }
    }.flowOn(Dispatchers.IO)
}
