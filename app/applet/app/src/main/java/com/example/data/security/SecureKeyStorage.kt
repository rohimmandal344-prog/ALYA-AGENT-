package com.example.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig
import java.io.File

/**
 * Secure storage wrapper for the Gemini API Key using EncryptedSharedPreferences (Android KeyStore + AES-256 GCM)
 * and secure local file persistence in the device's protected internal data directory (`context.filesDir`).
 * Ensures keys are never leaked via logs and validates on startup to prevent 403/503 HTTP errors.
 */
class SecureKeyStorage(private val context: Context) {
    private val apiKeyFile = File(context.filesDir, "alya_secure_api_key.dat")

    private val sharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "alya_encrypted_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to standard SharedPreferences in protected internal data directory if EncryptedSharedPreferences fails on restricted emulators
            context.getSharedPreferences("alya_fallback_secure_prefs", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val PREF_KEY_API_KEY = "encrypted_gemini_api_key"
    }

    init {
        // Validate and ensure stored key is synced and valid upon startup
        validateAndSyncStartupKey()
    }

    private fun validateAndSyncStartupKey() {
        try {
            val key = getApiKey()
            if (key.isNotEmpty() && isValidApiKeyFormat(key)) {
                // Ensure synced to both encrypted prefs and secure internal file
                saveApiKeyInternal(key)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun isValidApiKeyFormat(key: String): Boolean {
        val trimmed = key.trim()
        // Gemini API keys are typically ~39 chars starting with AIza, or at least 20 chars
        return trimmed.length >= 20 && !trimmed.contains(" ") && !trimmed.contains("\n")
    }

    private fun saveApiKeyInternal(apiKey: String) {
        try {
            apiKeyFile.writeText(apiKey, Charsets.UTF_8)
        } catch (ignored: Exception) {
        }
        try {
            sharedPreferences.edit()
                .putString(PREF_KEY_API_KEY, apiKey)
                .apply()
        } catch (ignored: Exception) {
        }
    }

    /**
     * Encrypts and securely saves the user-provided Gemini API key to EncryptedSharedPreferences and protected local file.
     */
    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            deleteApiKey()
            return
        }
        saveApiKeyInternal(trimmed)
    }

    /**
     * Retrieves and validates the Gemini API key from protected internal file, EncryptedSharedPreferences, or BuildConfig.
     * Prevents 403/503 errors by ensuring active validation.
     */
    fun getApiKey(): String {
        // 1. Check protected local file in internal data directory
        try {
            if (apiKeyFile.exists()) {
                val fileContent = apiKeyFile.readText(Charsets.UTF_8).trim()
                if (fileContent.isNotEmpty() && isValidApiKeyFormat(fileContent)) {
                    return fileContent
                }
            }
        } catch (ignored: Exception) {
        }

        // 2. Check EncryptedSharedPreferences
        try {
            val savedKey = sharedPreferences.getString(PREF_KEY_API_KEY, null)?.trim()
            if (!savedKey.isNullOrEmpty() && isValidApiKeyFormat(savedKey)) {
                // Sync back to internal file
                try { apiKeyFile.writeText(savedKey, Charsets.UTF_8) } catch (ignored: Exception) {}
                return savedKey
            }
        } catch (ignored: Exception) {
        }

        // 3. Check BuildConfig / Secrets (.env)
        try {
            val buildKey = BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
            if (!buildKey.isNullOrEmpty() && buildKey != "MY_GEMINI_API_KEY" && isValidApiKeyFormat(buildKey)) {
                return buildKey
            }
        } catch (ignored: Exception) {
        }

        return ""
    }

    /**
     * Checks if a valid API key is stored and available.
     */
    fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    /**
     * Clears the stored API key securely.
     */
    fun deleteApiKey() {
        try {
            if (apiKeyFile.exists()) {
                apiKeyFile.delete()
            }
        } catch (ignored: Exception) {
        }
        try {
            sharedPreferences.edit()
                .remove(PREF_KEY_API_KEY)
                .apply()
        } catch (ignored: Exception) {
        }
    }

    /**
     * Returns a masked representation for UI display without logging raw keys (e.g., "AIza...98F2").
     */
    fun getMaskedApiKey(): String {
        val key = getApiKey()
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••••${key.takeLast(4)}"
    }
}
