package com.example.data.api

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException

object GeminiAiService {
    private const val TAG = "GeminiAiService"
    
    // Lazy initialization as per skill guidelines
    private val model by lazy {
        Firebase.vertexAI.generativeModel("gemini-3.8-flash")
    }

    suspend fun generateContent(prompt: String, systemPrompt: String): String {
        try {
            // Mandated: realistic 60s timeout
            val response = withTimeout(60_000L) {
                model.generateContent(systemPrompt + "\n\n" + prompt)
            }
            return response.text ?: ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI generation failed", e)
            throw e
        }
    }
}
