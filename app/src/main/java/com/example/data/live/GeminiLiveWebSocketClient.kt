package com.example.data.live

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * WebSocket client communicating directly with Gemini Multimodal Live API
 * (BidiGenerateContent) for real-time bidirectional audio conversation.
 * Optimized for natural, low-latency conversational audio streaming.
 */
class GeminiLiveWebSocketClient(
    private val apiKey: String,
    private val model: String = "models/gemini-2.0-flash-exp",
    private val voiceName: String = "Aoede",
    private val systemPrompt: String,
    private val onStateChanged: (LiveConnectionState, String?) -> Unit,
    private val onAudioDataReceived: (ByteArray, Int) -> Unit,
    private val onTranscriptReceived: (String, Boolean) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onToolCallReceived: (id: String, name: String, args: Map<String, Any?>) -> Unit
) {
    companion object {
        private const val TAG = "GeminiLiveWS"
        private const val BASE_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val MAX_RECONNECT_ATTEMPTS = 3
    }

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val isManuallyClosed = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)
    private val currentTurnTranscript = StringBuilder()

    fun connect() {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank() || !trimmedKey.startsWith("AIza")) {
            onStateChanged(
                LiveConnectionState.ERROR,
                "Gemini API key is required. Please enter a valid Google AI Studio key starting with 'AIza' in Settings."
            )
            return
        }

        isManuallyClosed.set(false)
        onStateChanged(LiveConnectionState.CONNECTING, "Connecting to Gemini Live...")

        // Pass API key via URL query parameter and header for universal WebSocket support
        val wsUrl = "$BASE_WS_URL?key=$trimmedKey"
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("x-goog-api-key", trimmedKey)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully. Sending Gemini Live setup message...")
                reconnectAttempts.set(0)
                sendSetupMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing ($code): $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed ($code): $reason")
                if (!isManuallyClosed.get()) {
                    handleReconnectOrError("Live session closed ($code): $reason")
                } else {
                    onStateChanged(LiveConnectionState.DISCONNECTED, null)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorMsg = response?.message ?: t.localizedMessage ?: "WebSocket connection failed"
                Log.e(TAG, "WebSocket failure: $errorMsg (code=${response?.code})", t)
                if (!isManuallyClosed.get()) {
                    handleReconnectOrError(errorMsg)
                } else {
                    onStateChanged(LiveConnectionState.DISCONNECTED, null)
                }
            }
        })
    }

    private fun handleReconnectOrError(reason: String) {
        val attempts = reconnectAttempts.incrementAndGet()
        if (attempts <= MAX_RECONNECT_ATTEMPTS) {
            onStateChanged(LiveConnectionState.RECONNECTING, "Reconnecting ($attempts/$MAX_RECONNECT_ATTEMPTS)...")
            scope.launch {
                delay(1200L * attempts)
                if (!isManuallyClosed.get()) {
                    connect()
                }
            }
        } else {
            onStateChanged(
                LiveConnectionState.ERROR,
                "Unable to connect to Gemini Live: $reason. Please verify your API key and Internet connection."
            )
        }
    }

    private fun sendSetupMessage() {
        try {
            // Ensure live model is a valid BidiGenerateContent model
            val liveModel = when {
                model.contains("2.0-flash") -> if (model.startsWith("models/")) model else "models/$model"
                else -> "models/gemini-2.0-flash-exp"
            }

            // Normalize voice name to supported Gemini Live voices
            val normalizedVoice = when (voiceName.trim().lowercase()) {
                "aoede", "एडो", "aeda" -> "Aoede"
                "kore", "कोडे", "kora" -> "Kore"
                "ursa", "उरसा" -> "Ursa"
                "cinder", "cinderella", "सिंड्रेला" -> "Cinder"
                "fenrir" -> "Fenrir"
                "puck" -> "Puck"
                "charon" -> "Charon"
                else -> "Aoede"
            }

            val setupObj = JSONObject().apply {
                put("model", liveModel)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", normalizedVoice)
                            })
                        })
                    })
                })
                if (systemPrompt.isNotBlank()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", systemPrompt)
                        }))
                    })
                }
                put("tools", JSONArray().put(JSONObject().apply {
                    put("functionDeclarations", buildFunctionDeclarations())
                }))
            }

            val root = JSONObject().apply {
                put("setup", setupObj)
            }

            webSocket?.send(root.toString())
            Log.d(TAG, "Setup message sent with model=$liveModel, voice=$normalizedVoice")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating setup message: ${e.message}", e)
            onStateChanged(LiveConnectionState.ERROR, "Setup serialization error: ${e.message}")
        }
    }

    private fun buildFunctionDeclarations(): JSONArray {
        val declarations = JSONArray()

        // Flashlight tool
        declarations.put(JSONObject().apply {
            put("name", "toggle_flashlight")
            put("description", "Turns the device flashlight on or off.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("enabled", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "True to turn on, false to turn off")
                    })
                })
                put("required", JSONArray().put("enabled"))
            })
        })

        // Battery tool
        declarations.put(JSONObject().apply {
            put("name", "get_battery_status")
            put("description", "Checks device battery level and charging state.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        // Volume tool
        declarations.put(JSONObject().apply {
            put("name", "adjust_device_volume")
            put("description", "Adjusts media volume (UP, DOWN, MUTE, UNMUTE).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Action to perform: UP, DOWN, MUTE, or UNMUTE")
                    })
                })
                put("required", JSONArray().put("action"))
            })
        })

        // Open App tool
        declarations.put(JSONObject().apply {
            put("name", "open_application")
            put("description", "Opens an app on the device (youtube, camera, calculator, maps, settings, clock).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("app_name", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Name of the app: youtube, camera, calculator, maps, settings, clock")
                    })
                })
                put("required", JSONArray().put("app_name"))
            })
        })

        // Save memory tool
        declarations.put(JSONObject().apply {
            put("name", "save_memory")
            put("description", "Saves an approved user fact, preference, or conversation summary into long-term memory.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("category", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Category: PREFERENCE, FACT, or CONVERSATION_SUMMARY")
                    })
                    put("content", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Accurate detail to commit to long-term memory")
                    })
                })
                put("required", JSONArray().put("category").put("content"))
            })
        })

        return declarations
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)

            // 1. Setup complete
            if (json.has("setupComplete")) {
                Log.d(TAG, "Gemini Live setup complete confirmed!")
                onStateChanged(LiveConnectionState.CONNECTED, "Live connected")
                onStateChanged(LiveConnectionState.LISTENING, "Listening to your voice...")
                return
            }

            // 2. Server Content
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                // Check interruption (instant barge-in)
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Server signal: INTERRUPTED (barge-in)")
                    currentTurnTranscript.clear()
                    onInterrupted()
                    onStateChanged(LiveConnectionState.INTERRUPTED, "Interrupted")
                    onStateChanged(LiveConnectionState.LISTENING, "Listening to you...")
                    return
                }

                // Check model audio/text turn
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Transcript text
                            if (part.has("text")) {
                                val chunkText = part.getString("text")
                                currentTurnTranscript.append(chunkText)
                                onTranscriptReceived(currentTurnTranscript.toString(), false)
                            }

                            // Audio PCM chunk (16kHz or 24kHz)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "")
                                val base64Audio = inlineData.optString("data", "")
                                if (base64Audio.isNotEmpty()) {
                                    val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                                    val sampleRate = if (mimeType.contains("rate=16000")) 16000 else 24000
                                    onAudioDataReceived(audioBytes, sampleRate)
                                }
                            }
                        }
                    }
                }

                // Turn complete
                if (serverContent.optBoolean("turnComplete", false)) {
                    if (currentTurnTranscript.isNotEmpty()) {
                        onTranscriptReceived(currentTurnTranscript.toString(), true)
                        currentTurnTranscript.clear()
                    }
                }
            }

            // 3. Tool call
            if (json.has("toolCall")) {
                val toolCall = json.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    onStateChanged(LiveConnectionState.THINKING, "Executing device action...")
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val id = call.optString("id", "call_${System.currentTimeMillis()}")
                        val name = call.getString("name")
                        val argsObj = call.optJSONObject("args")
                        val argsMap = mutableMapOf<String, Any?>()
                        if (argsObj != null) {
                            val keys = argsObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                argsMap[key] = argsObj.get(key)
                            }
                        }
                        onToolCallReceived(id, name, argsMap)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming live message: ${e.message}", e)
        }
    }

    /**
     * Streams real-time 16kHz PCM audio chunk to Gemini Live API.
     */
    fun sendAudioChunk(pcmChunk: ByteArray) {
        if (webSocket == null || pcmChunk.isEmpty()) return
        try {
            val base64Data = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
            val msg = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Data)
                    }))
                })
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk: ${e.message}")
        }
    }

    /**
     * Sends a text turn or prompt into the live conversation session.
     */
    fun sendTextMessage(text: String) {
        if (webSocket == null || text.isBlank()) return
        try {
            onStateChanged(LiveConnectionState.THINKING, "Alya is thinking...")
            val msg = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", text)
                        }))
                    }))
                    put("turnComplete", true)
                })
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message: ${e.message}")
        }
    }

    /**
     * Sends tool response back to Gemini Live API.
     */
    fun sendToolResponse(callId: String, result: Map<String, Any?>) {
        try {
            val outputObj = JSONObject()
            result.forEach { (k, v) -> outputObj.put(k, v) }

            val msg = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", JSONArray().put(JSONObject().apply {
                        put("id", callId)
                        put("response", JSONObject().apply {
                            put("output", outputObj)
                        })
                    }))
                })
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response: ${e.message}")
        }
    }

    /**
     * Disconnects and cleans up the WebSocket connection.
     */
    fun disconnect() {
        isManuallyClosed.set(true)
        try {
            webSocket?.close(1000, "User disconnected")
            webSocket = null
            onStateChanged(LiveConnectionState.DISCONNECTED, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect: ${e.message}")
        }
    }
}
