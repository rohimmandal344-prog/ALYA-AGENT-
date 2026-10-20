package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores user-approved preferences, facts, and conversation summaries.
 * Strictly adheres to long-term memory requirements without fabrication.
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String, // "PREFERENCE", "FACT", "CONVERSATION_SUMMARY"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
