package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a conversation thread or voice call session in conversation history.
 */
@Entity(tableName = "conversation_sessions")
data class ConversationSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val title: String,
    val sessionType: String = "TEXT_CHAT", // "TEXT_CHAT", "LIVE_VOICE"
    val language: String = "Hindi",
    val messageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)
