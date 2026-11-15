package com.example.core.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAtMillis DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    fun observeConversation(conversationId: String): Flow<ConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(items: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(item: ConversationEntity)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :conversationId")
    suspend fun clearUnreadCount(conversationId: String)

    @Query("UPDATE conversations SET isBlockedByMe = :blocked WHERE peerId = :peerId")
    suspend fun updatePeerBlocked(peerId: String, blocked: Boolean)

    @Query("UPDATE conversations SET peerIsOnline = :online, peerLastSeenAt = :lastSeenAt WHERE peerId = :peerId")
    suspend fun updatePeerPresence(peerId: String, online: Boolean, lastSeenAt: String?)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtMillis ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id OR clientMessageId = :clientId LIMIT 1")
    suspend fun findMessage(id: String, clientId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(items: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(item: MessageEntity)

    @Query("DELETE FROM messages WHERE clientMessageId = :clientMessageId AND id != :serverId")
    suspend fun deleteTempMessageIfReplaced(clientMessageId: String, serverId: String)

    @Query("UPDATE messages SET deliveryStatus = :status, deliveredAtIso = COALESCE(:deliveredAt, deliveredAtIso), readAtIso = COALESCE(:readAt, readAtIso) WHERE id = :messageId")
    suspend fun updateMessageReceipt(
        messageId: String,
        status: String,
        deliveredAt: String?,
        readAt: String?
    )

    @Query("UPDATE messages SET deletedAtIso = :deletedAtIso, bodyText = NULL, decryptedText = NULL WHERE id = :messageId")
    suspend fun markMessageDeleted(messageId: String, deletedAtIso: String)

    @Query("SELECT * FROM messages WHERE deliveryStatus = 'FAILED' OR deliveryStatus = 'SENDING' ORDER BY createdAtMillis ASC")
    suspend fun getPendingOrFailedMessages(): List<MessageEntity>

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}
