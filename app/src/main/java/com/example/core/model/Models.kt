package com.example.core.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class MessageType {
    TEXT, IMAGE, VIDEO, VOICE, AUDIO, FILE
}

enum class MessageDeliveryStatus {
    SENDING, SENT, DELIVERED, SEEN, FAILED
}

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    @Json(name = "phone_e164") val phoneE164: String,
    @Json(name = "pin") val pin: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "device_public_key") val devicePublicKey: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "phone_e164") val phoneE164: String,
    @Json(name = "pin") val pin: String,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "device_public_key") val devicePublicKey: String
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "user") val user: UserProfileDto,
    @Json(name = "suspicious_login_notice") val suspiciousLoginNotice: String? = null
)

@JsonClass(generateAdapter = true)
data class UserProfileDto(
    @Json(name = "id") val id: String,
    @Json(name = "phone_e164") val phoneE164: String,
    @Json(name = "phone_verified") val phoneVerified: Boolean = false,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "about") val about: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "avatar_object_key") val avatarObjectKey: String? = null,
    @Json(name = "device_public_key") val devicePublicKey: String? = null,
    @Json(name = "is_online") val isOnline: Boolean = false,
    @Json(name = "last_seen_at") val lastSeenAt: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    @Json(name = "display_name") val displayName: String,
    @Json(name = "about") val about: String,
    @Json(name = "avatar_object_key") val avatarObjectKey: String? = null
)

@JsonClass(generateAdapter = true)
data class SearchUserRequest(
    @Json(name = "phone_e164") val phoneE164: String,
    @Json(name = "name_filter") val nameFilter: String? = null
)

@JsonClass(generateAdapter = true)
data class SearchUserResponse(
    @Json(name = "found") val found: Boolean,
    @Json(name = "user") val user: UserProfileDto? = null
)

@JsonClass(generateAdapter = true)
data class CreateConversationRequest(
    @Json(name = "peer_user_id") val peerUserId: String
)

@JsonClass(generateAdapter = true)
data class ConversationDto(
    @Json(name = "id") val id: String,
    @Json(name = "peer") val peer: UserProfileDto,
    @Json(name = "latest_message") val latestMessage: MessageDto? = null,
    @Json(name = "unread_count") val unreadCount: Int = 0,
    @Json(name = "is_blocked_by_me") val isBlockedByMe: Boolean = false,
    @Json(name = "has_blocked_me") val hasBlockedMe: Boolean = false,
    @Json(name = "updated_at") val updatedAt: String
)

@JsonClass(generateAdapter = true)
data class ConversationsListResponse(
    @Json(name = "conversations") val conversations: List<ConversationDto>
)

@JsonClass(generateAdapter = true)
data class MessageDto(
    @Json(name = "id") val id: String,
    @Json(name = "client_message_id") val clientMessageId: String,
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "message_type") val messageType: String,
    @Json(name = "body_text") val bodyText: String? = null,
    @Json(name = "is_encrypted") val isEncrypted: Boolean = false,
    @Json(name = "media_object_id") val mediaObjectId: String? = null,
    @Json(name = "media_file_name") val mediaFileName: String? = null,
    @Json(name = "media_mime_type") val mediaMimeType: String? = null,
    @Json(name = "media_size_bytes") val mediaSizeBytes: Long? = null,
    @Json(name = "media_duration_ms") val mediaDurationMs: Long? = null,
    @Json(name = "delivery_status") val deliveryStatus: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "delivered_at") val deliveredAt: String? = null,
    @Json(name = "read_at") val readAt: String? = null,
    @Json(name = "deleted_at") val deletedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class MessagesPageResponse(
    @Json(name = "messages") val messages: List<MessageDto>,
    @Json(name = "next_cursor") val nextCursor: String? = null,
    @Json(name = "has_more") val hasMore: Boolean = false
)

@JsonClass(generateAdapter = true)
data class SendMessageRequest(
    @Json(name = "client_message_id") val clientMessageId: String,
    @Json(name = "message_type") val messageType: String,
    @Json(name = "body_text") val bodyText: String? = null,
    @Json(name = "is_encrypted") val isEncrypted: Boolean = false,
    @Json(name = "media_object_id") val mediaObjectId: String? = null,
    @Json(name = "media_duration_ms") val mediaDurationMs: Long? = null
)

@JsonClass(generateAdapter = true)
data class PresignUploadRequest(
    @Json(name = "scope") val scope: String, // "profile" or "conversation"
    @Json(name = "conversation_id") val conversationId: String? = null,
    @Json(name = "media_category") val mediaCategory: String, // "profile", "images", "videos", "audio", "files"
    @Json(name = "file_name") val fileName: String,
    @Json(name = "mime_type") val mimeType: String,
    @Json(name = "size_bytes") val sizeBytes: Long
)

@JsonClass(generateAdapter = true)
data class PresignUploadResponse(
    @Json(name = "media_object_id") val mediaObjectId: String,
    @Json(name = "object_key") val objectKey: String,
    @Json(name = "upload_url") val uploadUrl: String,
    @Json(name = "expires_in_seconds") val expiresInSeconds: Int
)

@JsonClass(generateAdapter = true)
data class CompleteUploadRequest(
    @Json(name = "media_object_id") val mediaObjectId: String
)

@JsonClass(generateAdapter = true)
data class PresignDownloadResponse(
    @Json(name = "download_url") val downloadUrl: String,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "mime_type") val mimeType: String,
    @Json(name = "size_bytes") val sizeBytes: Long,
    @Json(name = "expires_in_seconds") val expiresInSeconds: Int
)

@JsonClass(generateAdapter = true)
data class BlockUserRequest(
    @Json(name = "target_user_id") val targetUserId: String
)

@JsonClass(generateAdapter = true)
data class ReportRequest(
    @Json(name = "target_user_id") val targetUserId: String? = null,
    @Json(name = "message_id") val messageId: String? = null,
    @Json(name = "reason") val reason: String
)

@JsonClass(generateAdapter = true)
data class IceServerDto(
    @Json(name = "urls") val urls: List<String>,
    @Json(name = "username") val username: String? = null,
    @Json(name = "credential") val credential: String? = null
)

@JsonClass(generateAdapter = true)
data class IceServersResponse(
    @Json(name = "ice_servers") val iceServers: List<IceServerDto>
)

@JsonClass(generateAdapter = true)
data class DeleteAccountRequest(
    @Json(name = "pin") val pin: String
)

@JsonClass(generateAdapter = true)
data class GenericOkResponse(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiErrorResponse(
    @Json(name = "error") val error: String? = null,
    @Json(name = "code") val code: String? = null,
    @Json(name = "retry_after_seconds") val retryAfterSeconds: Long? = null
)

@JsonClass(generateAdapter = true)
data class NotificationEndpointRequest(
    @Json(name = "transport") val transport: String,
    @Json(name = "endpoint_url") val endpointUrl: String
)

@JsonClass(generateAdapter = true)
data class RecoveryInfoResponse(
    @Json(name = "otp_enabled") val otpEnabled: Boolean = false,
    @Json(name = "policy_summary") val policySummary: String,
    @Json(name = "support_email") val supportEmail: String
)
