package com.example.util

object ApiKeyValidator {
    private const val API_KEY_PREFIX = "AIza"
    // Google Gemini API keys start with AIza and are typically 39 characters,
    // but can range between 35 and 60 characters with alphanumeric, underscores, and hyphens.
    private val API_KEY_REGEX = Regex("^AIza[A-Za-z0-9_-]{30,65}$")

    fun isValid(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val trimmed = key.trim()
        return API_KEY_REGEX.matches(trimmed)
    }

    fun clean(key: String?): String {
        return key?.trim() ?: ""
    }
}
