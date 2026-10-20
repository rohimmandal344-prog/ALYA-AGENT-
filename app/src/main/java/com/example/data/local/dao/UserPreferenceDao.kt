package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.UserPreferenceEntity
import com.example.data.local.entity.UserSettingsProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferenceDao {
    // --- Key-Value Preferences ---
    @Query("SELECT * FROM user_preferences")
    fun getAllPreferences(): Flow<List<UserPreferenceEntity>>

    @Query("SELECT * FROM user_preferences WHERE `key` = :key LIMIT 1")
    fun getPreferenceFlow(key: String): Flow<UserPreferenceEntity?>

    @Query("SELECT value FROM user_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreferenceValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePreference(preference: UserPreferenceEntity)

    @Query("DELETE FROM user_preferences WHERE `key` = :key")
    suspend fun deletePreference(key: String)

    @Query("DELETE FROM user_preferences")
    suspend fun clearPreferences()

    // --- Structured User Profile Preferences ---
    @Query("SELECT * FROM user_settings_profile WHERE id = 1 LIMIT 1")
    fun getSettingsProfileFlow(): Flow<UserSettingsProfileEntity?>

    @Query("SELECT * FROM user_settings_profile WHERE id = 1 LIMIT 1")
    suspend fun getSettingsProfileDirect(): UserSettingsProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserSettingsProfileEntity)

    @Query("UPDATE user_settings_profile SET selectedLanguage = :language, selectedLanguageCountry = :country, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateLanguage(language: String, country: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_settings_profile SET selectedVoiceName = :voiceName, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateVoice(voiceName: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_settings_profile SET isSubtitlesEnabled = :enabled, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateSubtitles(enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_settings_profile SET speechSpeed = :speed, pitch = :pitch, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateVoicePacing(speed: Float, pitch: Float, updatedAt: Long = System.currentTimeMillis())
}
