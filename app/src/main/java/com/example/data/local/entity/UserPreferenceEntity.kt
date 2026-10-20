package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Key-value pair entity for flexible and extensible user preferences in Room.
 */
@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val valueType: String = "STRING", // "STRING", "BOOLEAN", "INT", "FLOAT"
    val updatedAt: Long = System.currentTimeMillis()
)
