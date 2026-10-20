package com.example.data.live

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages Gemini Live API sessions using Kotlin Coroutines and StateFlow,
 * implementing robust auto-retry logic with exponential backoff and strict
 * mapping to the 10 required connection states:
 * IDLE, CONNECTING, CONNECTED, LISTENING, THINKING, SPEAKING, INTERRUPTED, RECONNECTING, ERROR, DISCONNECTED.
 */
class AlyaConnectionManager(
    private val context: Context,
    private val getApiKey: () -> String,
    private val getModel: () -> String,
    private val getVoiceName: () -> String,
    private val onAudioDataReceived: (ByteArray, Int) -> Unit,
    private val onTranscriptReceived: (String, Boolean) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onToolCallReceived: (id: String, name: String, args: Map<String, Any?>) -> Unit
) {
    companion object {
        private const val TAG = "AlyaConnectionManager"
        private const val MAX_RETRY_ATTEMPTS = 5
    }

    private val _liveState = MutableStateFlow(LiveConnectionState.IDLE)
    val liveState: StateFlow<LiveConnectionState> = _liveState.asStateFlow()

    private val _liveStatusMessage = MutableStateFlow<String?>(null)
    val liveStatusMessage: StateFlow<String?> = _liveStatusMessage.asStateFlow()

    private val _isLiveActive = MutableStateFlow(false)
    val isLiveActive: StateFlow<Boolean> = _isLiveActive.asStateFlow()

    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude: StateFlow<Float> = _inputAmplitude.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val managerScope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocketClient: GeminiLiveWebSocketClient? = null
    private var audioManager: AudioManager? = null
    private val retryAttempts = AtomicInteger(0)
    private val isManualStop = AtomicBoolean(false)
    private var lastSystemPrompt: String = ""

    fun startSession(systemPrompt: String) {
        lastSystemPrompt = systemPrompt
        val key = getApiKey()
        if (key.isBlank()) {
            _liveState.value = LiveConnectionState.ERROR
            _liveStatusMessage.value = "Gemini API key is missing. Please set your key in Settings."
            return
        }

        isManualStop.set(false)
        retryAttempts.set(0)
        stopSessionInternal()

        _isLiveActive.value = true
        _liveState.value = LiveConnectionState.CONNECTING
        _liveStatusMessage.value = "Connecting to Gemini Live..."
        _liveTranscript.value = ""

        managerScope.launch {
            try {
                audioManager = AudioManager(
                    context = context,
                    onAudioChunkRecorded = { chunk ->
                        if (_isLiveActive.value) {
                            webSocketClient?.sendAudioChunk(chunk)
                        }
                    },
                    onAmplitudeChanged = { inRms, outRms ->
                        _inputAmplitude.value = inRms
                        _outputAmplitude.value = outRms
                    },
                    onSpeakingStateChanged = { isSpeaking ->
                        if (_isLiveActive.value) {
                            if (isSpeaking) {
                                _liveState.value = LiveConnectionState.SPEAKING
                            } else if (_liveState.value == LiveConnectionState.SPEAKING) {
                                _liveState.value = LiveConnectionState.LISTENING
                            }
                        }
                    }
                )

                webSocketClient = GeminiLiveWebSocketClient(
                    apiKey = key,
                    model = getModel(),
                    voiceName = getVoiceName(),
                    systemPrompt = systemPrompt,
                    onStateChanged = { state, msg ->
                        handleWebSocketStateChange(state, msg)
                    },
                    onAudioDataReceived = { audioBytes, sampleRate ->
                        onAudioDataReceived(audioBytes, sampleRate)
                        audioManager?.enqueueAudio(audioBytes, sampleRate)
                    },
                    onTranscriptReceived = { text, isComplete ->
                        _liveTranscript.value = text
                        onTranscriptReceived(text, isComplete)
                    },
                    onInterrupted = {
                        audioManager?.bargeIn()
                        _liveState.value = LiveConnectionState.INTERRUPTED
                        onInterrupted()
                    },
                    onToolCallReceived = { id, name, args ->
                        onToolCallReceived(id, name, args)
                    }
                )

                val recordStarted = audioManager?.startRecording() ?: false
                audioManager?.startPlayback()

                if (!recordStarted) {
                    _liveState.value = LiveConnectionState.ERROR
                    _liveStatusMessage.value = "Failed to open microphone hardware."
                    return@launch
                }

                webSocketClient?.connect()

            } catch (e: Exception) {
                Log.e(TAG, "Error starting live session: ${e.message}", e)
                handleConnectionFailure("Startup error: ${e.localizedMessage}")
            }
        }
    }

    private fun handleWebSocketStateChange(state: LiveConnectionState, message: String?) {
        if (isManualStop.get()) return

        when (state) {
            LiveConnectionState.CONNECTED -> {
                retryAttempts.set(0)
                _liveState.value = LiveConnectionState.CONNECTED
                _liveStatusMessage.value = message
                managerScope.launch {
                    delay(300)
                    if (_liveState.value == LiveConnectionState.CONNECTED) {
                        _liveState.value = LiveConnectionState.LISTENING
                    }
                }
            }
            LiveConnectionState.ERROR, LiveConnectionState.DISCONNECTED -> {
                handleConnectionFailure(message ?: "Connection lost (HTTP 403/503 or network drop)")
            }
            else -> {
                _liveState.value = state
                if (message != null) {
                    _liveStatusMessage.value = message
                }
            }
        }
    }

    private fun handleConnectionFailure(reason: String) {
        if (isManualStop.get()) return

        val attempt = retryAttempts.incrementAndGet()
        if (attempt <= MAX_RETRY_ATTEMPTS) {
            // Exponential backoff: 1s, 2s, 4s, 8s, 16s
            val backoffMillis = (1000L * (1 shl (attempt - 1))).coerceAtMost(16000L)
            _liveState.value = LiveConnectionState.RECONNECTING
            _liveStatusMessage.value = "Connection dropped. Auto-retrying (Attempt $attempt/$MAX_RETRY_ATTEMPTS) in ${backoffMillis / 1000}s..."
            Log.w(TAG, "Connection failed: $reason. Reconnecting in ${backoffMillis}ms (Attempt $attempt)")

            managerScope.launch {
                delay(backoffMillis)
                if (!isManualStop.get() && _isLiveActive.value) {
                    webSocketClient?.connect()
                }
            }
        } else {
            _liveState.value = LiveConnectionState.ERROR
            _liveStatusMessage.value = "Failed after $MAX_RETRY_ATTEMPTS reconnection attempts. Reason: $reason"
            Log.e(TAG, "Max reconnection attempts reached. State set to ERROR.")
        }
    }

    fun retry() {
        retryAttempts.set(0)
        if (lastSystemPrompt.isNotBlank()) {
            startSession(lastSystemPrompt)
        }
    }

    fun stopSession() {
        isManualStop.set(true)
        _isLiveActive.value = false
        _liveState.value = LiveConnectionState.DISCONNECTED
        _liveStatusMessage.value = "Session disconnected"
        stopSessionInternal()
    }

    private fun stopSessionInternal() {
        try {
            webSocketClient?.disconnect()
            webSocketClient = null
            audioManager?.stopRecording()
            audioManager?.stopPlayback()
            audioManager = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping session: ${e.message}", e)
        }
    }
}
