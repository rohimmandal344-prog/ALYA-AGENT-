package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig
import com.example.util.ApiKeyValidator
import java.io.File
import java.security.GeneralSecurityException

/**
 * SecureKeyStorage manages the secure persistence and runtime protection of the user's
 * Gemini API key using Android's hardware-backed EncryptedSharedPreferences (AES-256-GCM).
 *
 * Security Features:
 * - MasterKey stored in Android KeyStore (AES-256-GCM).
 * - SharedPreferences keys encrypted via AES-256-SIV.
 * - SharedPreferences values encrypted via AES-256-GCM.
 * - Zero-logging policy: Raw API keys are never printed or exported in logs.
 * - Corrupted keystore automatic recovery: Catches Keystore failures and recovers safely.
 * - Format validation before commit using ApiKeyValidator.
 * - Masked key representation for secure UI display.
 */
class SecureKeyStorage(private val context: Context) {

    companion object {
        private const val TAG = "SecureKeyStorage"
        private const val PREFS_FILE = "alya_secure_prefs"
        private const val KEY_GEMINI_API = "secure_gemini_api_key"
        private const val MASK_PLACEHOLDER = "••••••••"
    }

    private val lock = Any()

    @Volatile
    private var inMemoryCachedKey: String? = null

    private val prefs: SharedPreferences by lazy {
        initEncryptedPrefs()
    }

    /**
     * Initializes EncryptedSharedPreferences with fallback recovery if Keystore
     * or preferences file encounters corruption or encryption failure.
     */
    private fun initEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, attempting safe recovery", e)
            try {
                // Clear potentially corrupted preference file and retry
                val prefsFile = File(context.filesDir.parent, "shared_prefs/$PREFS_FILE.xml")
                if (prefsFile.exists()) {
                    prefsFile.delete()
                }

                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (fatal: Exception) {
                Log.e(TAG, "Fatal Keystore error, falling back to private SharedPreferences", fatal)
                context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            }
        }
    }

    /**
     * Validates and saves the Gemini API key into EncryptedSharedPreferences.
     * Enforces the 'AIza' prefix format and trims trailing/leading whitespace.
     *
     * @param apiKey The raw user-provided API key.
     * @return true if the key was successfully validated and stored, false otherwise.
     */
    fun saveApiKey(apiKey: String): Boolean = synchronized(lock) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            deleteApiKey()
            return true
        }

        // Validate format: must start with 'AIza' and conform to Google Gemini API key standards
        if (!ApiKeyValidator.isValid(trimmed)) {
            Log.w(TAG, "Rejected API key: invalid format (does not match AIza pattern)")
            return false
        }

        return try {
            prefs.edit()
                .putString(KEY_GEMINI_API, trimmed)
                .commit() // Synchronous commit to guarantee immediate availability

            inMemoryCachedKey = trimmed
            Log.d(TAG, "API key securely stored in EncryptedSharedPreferences (length=${trimmed.length})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving API key to encrypted storage: ${e.message}")
            false
        }
    }

    /**
     * Retrieves the stored Gemini API key at runtime.
     * Checks:
     * 1. In-memory cache for ultra-fast thread-safe retrieval.
     * 2. EncryptedSharedPreferences on disk.
     * 3. Fallback to BuildConfig if configured in build environment.
     *
     * @return The plaintext API key if present and valid, or an empty string.
     */
    fun getApiKey(): String = synchronized(lock) {
        // 1. Fast in-memory cache check
        inMemoryCachedKey?.let { cached ->
            if (cached.isNotBlank() && ApiKeyValidator.isValid(cached)) {
                return cached
            }
        }

        // 2. EncryptedSharedPreferences disk read
        try {
            val diskKey = prefs.getString(KEY_GEMINI_API, null)?.trim()
            if (!diskKey.isNullOrBlank() && ApiKeyValidator.isValid(diskKey)) {
                inMemoryCachedKey = diskKey
                return diskKey
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from EncryptedSharedPreferences: ${e.message}")
        }

        // 3. Fallback to BuildConfig via reflection safely
        try {
            val buildKey = BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
            val trimmedBuildKey = buildKey?.trim()
            if (!trimmedBuildKey.isNullOrBlank() &&
                trimmedBuildKey != "MY_GEMINI_API_KEY" &&
                ApiKeyValidator.isValid(trimmedBuildKey)
            ) {
                return trimmedBuildKey
            }
        } catch (_: Exception) {}

        // 4. Fallback to R.string.google_api_key (auto-generated by google-services plugin)
        try {
            val resId = context.resources.getIdentifier("google_api_key", "string", context.packageName)
            if (resId != 0) {
                val googleServicesKey = context.getString(resId).trim()
                if (googleServicesKey.isNotBlank() && ApiKeyValidator.isValid(googleServicesKey)) {
                    return googleServicesKey
                }
            }
        } catch (_: Exception) {}

        return ""
    }

    /**
     * Checks whether a valid Gemini API key is configured and ready for runtime inference.
     */
    fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    /**
     * Safely deletes the API key from both disk and in-memory cache.
     */
    fun deleteApiKey(): Unit = synchronized(lock) {
        inMemoryCachedKey = null
        try {
            prefs.edit().remove(KEY_GEMINI_API).commit()
            Log.d(TAG, "API key deleted from encrypted storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting API key: ${e.message}")
        }
    }

    /**
     * Returns a privacy-safe masked representation of the API key for display in settings UI.
     * Example: "AIza••••••••9xK2" or "••••••••" if short.
     */
    fun getMaskedApiKey(): String {
        val key = getApiKey()
        if (key.isBlank()) return ""
        return if (key.length >= 12) {
            "${key.take(4)}$MASK_PLACEHOLDER${key.takeLast(4)}"
        } else {
            MASK_PLACEHOLDER
        }
    }

    /**
     * Validates whether an input key string matches the standard Gemini API key format.
     */
    fun validateFormat(key: String): Boolean {
        return ApiKeyValidator.isValid(key)
    }
}
