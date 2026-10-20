package com.example.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class KeyValidationResult {
    data object Success : KeyValidationResult()
    data class Error(val message: String) : KeyValidationResult()
}

sealed class ChatResponseResult {
    data class Success(val text: String, val toolCall: ToolCallData? = null) : ChatResponseResult()
    data class Error(val message: String) : ChatResponseResult()
}

data class ToolCallData(
    val name: String,
    val args: Map<String, Any?>
)

class GeminiRestClient(private val apiKey: String) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("x-goog-api-key", apiKey.trim())
                .build()
            chain.proceed(request)
        }
        .build()

    companion object {
        private const val TAG = "GeminiRestClient"
        private const val BASE_REST_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    /**
     * Validates a candidate Gemini API key against Google's API.
     * Uses standard models (gemini-1.5-flash and gemini-2.0-flash) with comprehensive
     * error body parsing (handles 200 OK, 429 quota exhaustion as valid key, and parses 400/403).
     */
    suspend fun validateApiKey(testKey: String): KeyValidationResult = withContext(Dispatchers.IO) {
        val trimmed = testKey.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("AIza")) {
            return@withContext KeyValidationResult.Error("API key must begin with the standard 'AIza' prefix.")
        }

        val testModels = listOf("gemini-1.5-flash", "gemini-2.0-flash")
        var lastError = "Unable to connect to Google Gemini API"

        for (testModel in testModels) {
            val url = "$BASE_REST_URL/$testModel:generateContent?key=$trimmed"
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply { put("text", "hi") }))
                }))
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", trimmed)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val tempClient = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val response = tempClient.newCall(request).execute()
                val responseBody = response.body?.string().orEmpty()

                when (response.code) {
                    200 -> return@withContext KeyValidationResult.Success
                    429 -> {
                        // 429 means rate-limited / quota exhausted, but the key is 100% valid!
                        Log.i(TAG, "API Key is valid but quota-limited (429)")
                        return@withContext KeyValidationResult.Success
                    }
                    400, 403 -> {
                        if (responseBody.contains("API_KEY_INVALID") || responseBody.contains("API key not valid")) {
                            return@withContext KeyValidationResult.Error("Google rejected this key: API key is not valid or restricted.")
                        } else if (responseBody.contains("SERVICE_DISABLED")) {
                            return@withContext KeyValidationResult.Error("Generative Language API is disabled for this Google Cloud project.")
                        } else {
                            lastError = "Google API Error (${response.code})"
                        }
                    }
                    404 -> {
                        // Model might not exist on this tier, try next model in list
                        lastError = "Model $testModel not found for this tier."
                    }
                    else -> {
                        lastError = "HTTP ${response.code}: $responseBody"
                    }
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Network connection failed"
            }
        }

        KeyValidationResult.Error(lastError)
    }

    suspend fun generateChatResponse(
        systemPrompt: String,
        messages: List<Pair<String, String>>,
        model: String = "gemini-1.5-flash"
    ): ChatResponseResult = withContext(Dispatchers.IO) {
        // Normalize model name for REST API
        val cleanModel = when {
            model.contains("1.5-flash") -> "gemini-1.5-flash"
            model.contains("2.0-flash") -> "gemini-2.0-flash"
            model.contains("1.5-pro") -> "gemini-1.5-pro"
            else -> "gemini-1.5-flash"
        }
        val url = "$BASE_REST_URL/$cleanModel:generateContent?key=${apiKey.trim()}"

        val contentsArray = JSONArray()
        for ((role, text) in messages) {
            contentsArray.put(JSONObject().apply {
                put("role", if (role == "user") "user" else "model")
                put("parts", JSONArray().put(JSONObject().apply { put("text", text) }))
            })
        }

        val payload = JSONObject().apply {
            put("contents", contentsArray)
            if (systemPrompt.isNotBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply { put("text", systemPrompt) }))
                })
            }
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey.trim())
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (response.code == 200) {
                val json = JSONObject(bodyString)
                val replyText = json.optJSONArray("candidates")?.getJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.getJSONObject(0)
                    ?.optString("text", "") ?: ""
                ChatResponseResult.Success(replyText)
            } else {
                Log.e(TAG, "Chat response error ${response.code}: $bodyString")
                ChatResponseResult.Error("API error (${response.code}). Check your key and quota.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during chat request: ${e.message}", e)
            ChatResponseResult.Error(e.localizedMessage ?: "Network error during response generation")
        }
    }
}
