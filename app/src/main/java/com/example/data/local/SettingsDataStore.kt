package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    companion object {
        val LIVE_MODEL_KEY = stringPreferencesKey("live_model")
        val LIVE_VOICE_KEY = stringPreferencesKey("live_voice")
        val SELECTED_LANGUAGE_KEY = stringPreferencesKey("selected_language")
        val SUBTITLES_ENABLED_KEY = booleanPreferencesKey("enable_subtitles")
        val PRIVACY_POLICY_ACCEPTED_KEY = booleanPreferencesKey("privacy_policy_accepted")
    }

    val liveModelFlow: Flow<String> = context.dataStore.data.map { it[LIVE_MODEL_KEY] ?: "models/gemini-3.8-flash" }
    val liveVoiceFlow: Flow<String> = context.dataStore.data.map { it[LIVE_VOICE_KEY] ?: "Aoede" }
    val selectedLanguageFlow: Flow<String> = context.dataStore.data.map { it[SELECTED_LANGUAGE_KEY] ?: "Hindi" }
    val subtitlesEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[SUBTITLES_ENABLED_KEY] ?: true }
    val privacyPolicyAcceptedFlow: Flow<Boolean> = context.dataStore.data.map { it[PRIVACY_POLICY_ACCEPTED_KEY] ?: false }

    suspend fun setLiveModel(model: String) { context.dataStore.edit { it[LIVE_MODEL_KEY] = model } }
    suspend fun setLiveVoice(voice: String) { context.dataStore.edit { it[LIVE_VOICE_KEY] = voice } }
    suspend fun setSelectedLanguage(language: String) { context.dataStore.edit { it[SELECTED_LANGUAGE_KEY] = language } }
    suspend fun setSubtitlesEnabled(enabled: Boolean) { context.dataStore.edit { it[SUBTITLES_ENABLED_KEY] = enabled } }
    suspend fun setPrivacyPolicyAccepted(accepted: Boolean) { context.dataStore.edit { it[PRIVACY_POLICY_ACCEPTED_KEY] = accepted } }
}
