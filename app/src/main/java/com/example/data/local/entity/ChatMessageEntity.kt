package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores chat conversation messages with timestamps, session association, and modality indicators.
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["sessionId"]), Index(value = ["timestamp"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String = "default_session",
    val role: String, // "user", "alya", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isLiveAudio: Boolean = false,
    val audioDurationMs: Long = 0L,
    val detectedLanguage: String? = null
)
