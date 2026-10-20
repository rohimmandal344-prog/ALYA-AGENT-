package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.ChatMessageDao
import com.example.data.local.dao.ConversationSessionDao
import com.example.data.local.dao.MemoryDao
import com.example.data.local.dao.UserPreferenceDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.ConversationSessionEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.UserPreferenceEntity
import com.example.data.local.entity.UserSettingsProfileEntity

/**
 * Room Database managing conversation history, user preferences, and approved memories.
 */
@Database(
    entities = [
        MemoryEntity::class,
        ChatMessageEntity::class,
        ConversationSessionEntity::class,
        UserPreferenceEntity::class,
        UserSettingsProfileEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun conversationSessionDao(): ConversationSessionDao
    abstract fun userPreferenceDao(): UserPreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "alya_assistant.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
