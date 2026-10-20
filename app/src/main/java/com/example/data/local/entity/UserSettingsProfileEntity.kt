package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Structured user preferences profile stored durably in Room.
 * Uses a singleton row (id = 1) for clean reactive observation via Room Flow.
 */
@Entity(tableName = "user_settings_profile")
data class UserSettingsProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val selectedLanguage: String = "Hindi",
    val selectedLanguageCountry: String = "India",
    val selectedVoiceName: String = "Aoede",
    val selectedLiveModel: String = "models/gemini-3.8-flash",
    val isSubtitlesEnabled: Boolean = true,
    val speechSpeed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val warmEmpatheticTone: Boolean = true,
    val autoBargeInEnabled: Boolean = true,
    val noiseSuppressionEnabled: Boolean = true,
    val echoCancellationEnabled: Boolean = true,
    val autoScrollChat: Boolean = true,
    val userName: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
