package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ConversationSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationSessionDao {
    @Query("SELECT * FROM conversation_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ConversationSessionEntity>>

    @Query("SELECT * FROM conversation_sessions WHERE sessionId = :sessionId LIMIT 1")
    fun getSessionFlow(sessionId: String): Flow<ConversationSessionEntity?>

    @Query("SELECT * FROM conversation_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionDirect(sessionId: String): ConversationSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ConversationSessionEntity)

    @Update
    suspend fun updateSession(session: ConversationSessionEntity)

    @Query("UPDATE conversation_sessions SET messageCount = messageCount + 1, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun incrementMessageCount(sessionId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversation_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM conversation_sessions")
    suspend fun clearAllSessions()
}
