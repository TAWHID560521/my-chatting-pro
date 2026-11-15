package com.example.core.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val peerPhoneE164: String,
    val peerDisplayName: String,
    val peerAbout: String,
    val peerAvatarUrl: String?,
    val peerPublicKey: String?,
    val peerIsOnline: Boolean,
    val peerLastSeenAt: String?,
    val latestMessageText: String?,
    val latestMessageType: String?,
    val latestMessageStatus: String?,
    val latestMessageSenderId: String?,
    val latestMessageAt: String?,
    val unreadCount: Int,
    val isBlockedByMe: Boolean,
    val hasBlockedMe: Boolean,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId", "createdAtMillis"]),
        Index(value = ["clientMessageId"], unique = true)
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val clientMessageId: String,
    val conversationId: String,
    val senderId: String,
    val messageType: String,
    val bodyText: String?,
    val decryptedText: String?,
    val isEncrypted: Boolean,
    val mediaObjectId: String?,
    val mediaFileName: String?,
    val mediaMimeType: String?,
    val mediaSizeBytes: Long?,
    val mediaDurationMs: Long?,
    val localAttachmentUri: String?,
    val deliveryStatus: String,
    val createdAtIso: String,
    val createdAtMillis: Long,
    val deliveredAtIso: String?,
    val readAtIso: String?,
    val deletedAtIso: String?
)
