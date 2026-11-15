package com.example.core.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import com.example.core.crypto.E2eeManager
import com.example.core.local.AppDatabase
import com.example.core.local.ConversationEntity
import com.example.core.local.MessageEntity
import com.example.core.local.PreferencesManager
import com.example.core.localization.DateTimeFormatterUtil
import com.example.core.media.MediaCompressor
import com.example.core.media.PreparedMediaFile
import com.example.core.model.AuthResponse
import com.example.core.model.BlockUserRequest
import com.example.core.model.CompleteUploadRequest
import com.example.core.model.ConversationDto
import com.example.core.model.CreateConversationRequest
import com.example.core.model.DeleteAccountRequest
import com.example.core.model.LoginRequest
import com.example.core.model.MessageDto
import com.example.core.model.PresignUploadRequest
import com.example.core.model.RecoveryInfoResponse
import com.example.core.model.RegisterRequest
import com.example.core.model.ReportRequest
import com.example.core.model.SearchUserRequest
import com.example.core.model.SearchUserResponse
import com.example.core.model.SendMessageRequest
import com.example.core.model.UpdateProfileRequest
import com.example.core.model.UserProfileDto
import com.example.core.network.ApiService
import com.example.core.network.NetworkClient
import com.example.core.network.S3MediaTransferClient
import com.example.core.network.WebSocketManager
import com.example.core.network.WsEvent
import com.example.core.notifications.AppNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

sealed class AppError(val code: String) : Exception() {
    data object WrongPin : AppError("WRONG_PIN")
    data object AccountNotFound : AppError("ACCOUNT_NOT_FOUND")
    data object DuplicateAccount : AppError("DUPLICATE_ACCOUNT")
    data class RateLimited(val retryAfterSeconds: Long?) : AppError("RATE_LIMITED")
    data object NetworkFailure : AppError("NETWORK_FAILURE")
    data object BackendUnavailable : AppError("BACKEND_UNAVAILABLE")
    data object StorageTransferFailure : AppError("STORAGE_FAILURE")
    data class ServerMessage(val serverMessage: String) : AppError("SERVER_ERROR")
    data object Unknown : AppError("UNKNOWN")
}

class ChatRepository(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val database: AppDatabase,
    private val networkClient: NetworkClient,
    private val apiService: ApiService,
    private val webSocketManager: WebSocketManager,
    private val s3TransferClient: S3MediaTransferClient,
    private val e2eeManager: E2eeManager,
    private val notificationManager: AppNotificationManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao = database.chatDao()

    private val _uploadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val uploadProgress: StateFlow<Map<String, Float>> = _uploadProgress.asStateFlow()

    private val _typingPeers = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingPeers: StateFlow<Map<String, Boolean>> = _typingPeers.asStateFlow()

    @Volatile
    var activeConversationId: String? = null

    init {
        scope.launch {
            webSocketManager.events.collect { event ->
                handleWsEvent(event)
            }
        }
    }

    fun observeConversations(): Flow<List<ConversationEntity>> = dao.observeConversations()

    fun observeConversation(conversationId: String): Flow<ConversationEntity?> =
        dao.observeConversation(conversationId)

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        dao.observeMessages(conversationId)

    suspend fun getOrCreateDeviceIdentity(): E2eeManager.IdentityKeyPair {
        val (pub, priv) = preferencesManager.getE2eeKeyPairRaw()
        return e2eeManager.generateOrLoadIdentity(pub, priv) { generated ->
            scope.launch {
                preferencesManager.saveE2eeKeyPairRaw(
                    generated.publicKeyBase64,
                    generated.wrappedPrivateKeyBase64
                )
            }
        }
    }

    suspend fun register(phoneE164: String, pin: String, displayName: String): Result<AuthResponse> {
        return try {
            val identity = getOrCreateDeviceIdentity()
            val response = apiService.register(
                RegisterRequest(
                    phoneE164 = phoneE164,
                    pin = pin,
                    displayName = displayName,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                    devicePublicKey = identity.publicKeyBase64
                )
            )
            handleAuthResponse(response)
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun login(phoneE164: String, pin: String): Result<AuthResponse> {
        return try {
            val identity = getOrCreateDeviceIdentity()
            val response = apiService.login(
                LoginRequest(
                    phoneE164 = phoneE164,
                    pin = pin,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                    devicePublicKey = identity.publicKeyBase64
                )
            )
            handleAuthResponse(response)
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun fetchRecoveryInfo(): Result<RecoveryInfoResponse> {
        return try {
            val resp = apiService.getRecoveryInfo()
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(mapHttpError(resp))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun logout() {
        try {
            apiService.logout()
        } catch (_: Exception) {
        } finally {
            webSocketManager.stop()
            dao.clearAllMessages()
            dao.clearAllConversations()
            preferencesManager.clearSession()
        }
    }

    suspend fun deleteAccount(pin: String): Result<Unit> {
        return try {
            val resp = apiService.deleteAccount(DeleteAccountRequest(pin = pin))
            if (resp.isSuccessful) {
                webSocketManager.stop()
                dao.clearAllMessages()
                dao.clearAllConversations()
                MediaCompressor.clearTemporaryCacheOnly(context)
                preferencesManager.clearSession()
                Result.success(Unit)
            } else {
                Result.failure(mapHttpError(resp))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun refreshMyProfile(): Result<UserProfileDto> {
        return try {
            val resp = apiService.getMyProfile()
            val user = resp.body()
            if (resp.isSuccessful && user != null) {
                preferencesManager.updateProfileCache(user.displayName, user.about, user.avatarUrl)
                Result.success(user)
            } else {
                Result.failure(mapHttpError(resp))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun updateProfile(
        displayName: String,
        about: String,
        newAvatarUri: Uri?
    ): Result<UserProfileDto> {
        return try {
            var avatarObjectKey: String? = null
            if (newAvatarUri != null) {
                val compressed = MediaCompressor.compressImageUri(context, newAvatarUri)
                    .getOrElse { return Result.failure(AppError.StorageTransferFailure) }

                val presignResp = apiService.presignUpload(
                    PresignUploadRequest(
                        scope = "profile",
                        conversationId = null,
                        mediaCategory = "profile",
                        fileName = compressed.fileName,
                        mimeType = compressed.mimeType,
                        sizeBytes = compressed.sizeBytes
                    )
                )
                val presign = presignResp.body()
                if (!presignResp.isSuccessful || presign == null) {
                    compressed.file.delete()
                    return Result.failure(mapHttpError(presignResp))
                }

                val uploadResult = s3TransferClient.uploadFileToPresignedUrl(
                    transferId = "avatar_${UUID.randomUUID()}",
                    uploadUrl = presign.uploadUrl,
                    file = compressed.file,
                    mimeType = compressed.mimeType,
                    onProgress = {}
                )
                compressed.file.delete()
                if (uploadResult.isFailure) {
                    return Result.failure(AppError.StorageTransferFailure)
                }

                val completeResp = apiService.completeUpload(CompleteUploadRequest(presign.mediaObjectId))
                if (!completeResp.isSuccessful) {
                    return Result.failure(mapHttpError(completeResp))
                }
                avatarObjectKey = presign.objectKey
            }

            val updateResp = apiService.updateMyProfile(
                UpdateProfileRequest(
                    displayName = displayName,
                    about = about,
                    avatarObjectKey = avatarObjectKey
                )
            )
            val updated = updateResp.body()
            if (updateResp.isSuccessful && updated != null) {
                preferencesManager.updateProfileCache(updated.displayName, updated.about, updated.avatarUrl)
                Result.success(updated)
            } else {
                Result.failure(mapHttpError(updateResp))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun searchUser(phoneE164: String, nameFilter: String?): Result<SearchUserResponse> {
        return try {
            val resp = apiService.searchUser(
                SearchUserRequest(
                    phoneE164 = phoneE164,
                    nameFilter = nameFilter?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(mapHttpError(resp))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun createOrGetConversation(peerUserId: String): Result<ConversationEntity> {
        return try {
            val resp = apiService.createOrGetConversation(CreateConversationRequest(peerUserId))
            val dto = resp.body()
            if (resp.isSuccessful && dto != null) {
                val entity = mapConversationToEntity(dto)
                dao.upsertConversation(entity)
                Result.success(entity)
            } else {
                Result.failure(mapHttpError(resp))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun refreshConversations(): Result<Unit> {
        return try {
            val resp = apiService.getConversations()
            val body = resp.body()
            if (resp.isSuccessful && body != null) {
                val entities = body.conversations.map { mapConversationToEntity(it) }
                dao.upsertConversations(entities)
                Result.success(Unit)
            } else {
                Result.failure(mapHttpError(resp))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun loadMessagesPage(
        conversationId: String,
        cursor: String?,
        peerPublicKey: String?
    ): Result<Pair<String?, Boolean>> {
        return try {
            val resp = apiService.getMessages(conversationId = conversationId, cursor = cursor, limit = 30)
            val body = resp.body()
            if (resp.isSuccessful && body != null) {
                val entities = body.messages.map { dto ->
                    mapMessageToEntity(dto, peerPublicKey)
                }
                dao.upsertMessages(entities)
                Result.success(body.nextCursor to body.hasMore)
            } else {
                Result.failure(mapHttpError(resp))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun markConversationRead(conversationId: String) {
        dao.clearUnreadCount(conversationId)
        try {
            apiService.markConversationRead(conversationId)
        } catch (_: Exception) {
        }
    }

    suspend fun sendTextMessage(
        conversationId: String,
        plaintext: String,
        peerPublicKey: String?
    ): Result<Unit> {
        val session = preferencesManager.currentSession()
        val clientMessageId = UUID.randomUUID().toString()
        val nowIso = currentUtcIso()
        val nowMillis = DateTimeFormatterUtil.parseUtcTimestampMillis(nowIso)

        val (bodyToSend, isEncrypted) = encryptIfPossible(plaintext, peerPublicKey, conversationId)

        val tempEntity = MessageEntity(
            id = "local_$clientMessageId",
            clientMessageId = clientMessageId,
            conversationId = conversationId,
            senderId = session.userId,
            messageType = "TEXT",
            bodyText = bodyToSend,
            decryptedText = plaintext,
            isEncrypted = isEncrypted,
            mediaObjectId = null,
            mediaFileName = null,
            mediaMimeType = null,
            mediaSizeBytes = null,
            mediaDurationMs = null,
            localAttachmentUri = null,
            deliveryStatus = "SENDING",
            createdAtIso = nowIso,
            createdAtMillis = nowMillis,
            deliveredAtIso = null,
            readAtIso = null,
            deletedAtIso = null
        )
        dao.upsertMessage(tempEntity)

        return postMessageToServer(tempEntity, peerPublicKey)
    }

    suspend fun sendMediaMessage(
        conversationId: String,
        messageType: String,
        mediaCategory: String,
        preparedFile: PreparedMediaFile,
        durationMs: Long?,
        peerPublicKey: String?
    ): Result<Unit> {
        val session = preferencesManager.currentSession()
        val clientMessageId = UUID.randomUUID().toString()
        val nowIso = currentUtcIso()
        val nowMillis = DateTimeFormatterUtil.parseUtcTimestampMillis(nowIso)

        val tempEntity = MessageEntity(
            id = "local_$clientMessageId",
            clientMessageId = clientMessageId,
            conversationId = conversationId,
            senderId = session.userId,
            messageType = messageType,
            bodyText = preparedFile.fileName,
            decryptedText = preparedFile.fileName,
            isEncrypted = false,
            mediaObjectId = null,
            mediaFileName = preparedFile.fileName,
            mediaMimeType = preparedFile.mimeType,
            mediaSizeBytes = preparedFile.sizeBytes,
            mediaDurationMs = durationMs,
            localAttachmentUri = preparedFile.file.absolutePath,
            deliveryStatus = "SENDING",
            createdAtIso = nowIso,
            createdAtMillis = nowMillis,
            deliveredAtIso = null,
            readAtIso = null,
            deletedAtIso = null
        )
        dao.upsertMessage(tempEntity)

        return uploadAndSendMediaEntity(tempEntity, mediaCategory, preparedFile, peerPublicKey)
    }

    fun cancelUpload(clientMessageId: String) {
        s3TransferClient.cancelTransfer(clientMessageId)
        _uploadProgress.update { it - clientMessageId }
    }

    suspend fun retryFailedMessage(message: MessageEntity, peerPublicKey: String?): Result<Unit> {
        val updated = message.copy(deliveryStatus = "SENDING")
        dao.upsertMessage(updated)

        return if (message.messageType == "TEXT" || message.mediaObjectId != null) {
            postMessageToServer(updated, peerPublicKey)
        } else {
            val localPath = message.localAttachmentUri
            if (localPath.isNullOrBlank() || !File(localPath).exists()) {
                dao.upsertMessage(message.copy(deliveryStatus = "FAILED"))
                return Result.failure(AppError.StorageTransferFailure)
            }
            val file = File(localPath)
            val category = when (message.messageType) {
                "IMAGE" -> "images"
                "VIDEO" -> "videos"
                "VOICE", "AUDIO" -> "audio"
                else -> "files"
            }
            val prepared = PreparedMediaFile(
                file = file,
                fileName = message.mediaFileName ?: file.name,
                mimeType = message.mediaMimeType ?: "application/octet-stream",
                sizeBytes = file.length()
            )
            uploadAndSendMediaEntity(updated, category, prepared, peerPublicKey)
        }
    }

    suspend fun downloadMediaToSafeFile(
        mediaObjectId: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): Result<File> {
        return try {
            val existing = s3TransferClient.getSafeDownloadFile(mediaObjectId, fileName)
            if (existing.exists() && existing.length() > 0L) {
                onProgress(1f)
                return Result.success(existing)
            }
            val presignResp = apiService.presignDownload(mediaObjectId)
            val body = presignResp.body()
            if (!presignResp.isSuccessful || body == null) {
                return Result.failure(mapHttpError(presignResp))
            }
            s3TransferClient.downloadFileFromPresignedUrl(
                transferId = mediaObjectId,
                downloadUrl = body.downloadUrl,
                destinationFile = existing,
                onProgress = onProgress
            )
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun getPresignedDownloadUrl(mediaObjectId: String): Result<String> {
        return try {
            val resp = apiService.presignDownload(mediaObjectId)
            val body = resp.body()
            if (resp.isSuccessful && body != null) {
                Result.success(body.downloadUrl)
            } else {
                Result.failure(mapHttpError(resp))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun deleteOwnMessage(messageId: String): Result<Unit> {
        return try {
            val resp = apiService.deleteOwnMessage(messageId)
            if (resp.isSuccessful) {
                dao.markMessageDeleted(messageId, currentUtcIso())
                Result.success(Unit)
            } else {
                Result.failure(mapHttpError(resp))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun setUserBlocked(peerUserId: String, block: Boolean): Result<Unit> {
        return try {
            val resp = if (block) {
                apiService.blockUser(BlockUserRequest(peerUserId))
            } else {
                apiService.unblockUser(BlockUserRequest(peerUserId))
            }
            if (resp.isSuccessful) {
                dao.updatePeerBlocked(peerUserId, block)
                Result.success(Unit)
            } else {
                Result.failure(mapHttpError(resp))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    suspend fun submitReport(targetUserId: String?, messageId: String?, reason: String): Result<Unit> {
        return try {
            val resp = apiService.submitReport(ReportRequest(targetUserId, messageId, reason))
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(mapHttpError(resp))
        } catch (e: Exception) {
            Result.failure(mapNetworkException(e))
        }
    }

    private suspend fun uploadAndSendMediaEntity(
        tempEntity: MessageEntity,
        mediaCategory: String,
        prepared: PreparedMediaFile,
        peerPublicKey: String?
    ): Result<Unit> {
        return try {
            _uploadProgress.update { it + (tempEntity.clientMessageId to 0.02f) }
            val presignResp = apiService.presignUpload(
                PresignUploadRequest(
                    scope = "conversation",
                    conversationId = tempEntity.conversationId,
                    mediaCategory = mediaCategory,
                    fileName = prepared.fileName,
                    mimeType = prepared.mimeType,
                    sizeBytes = prepared.sizeBytes
                )
            )
            val presign = presignResp.body()
            if (!presignResp.isSuccessful || presign == null) {
                _uploadProgress.update { it - tempEntity.clientMessageId }
                dao.upsertMessage(tempEntity.copy(deliveryStatus = "FAILED"))
                return Result.failure(mapHttpError(presignResp))
            }

            val uploadRes = s3TransferClient.uploadFileToPresignedUrl(
                transferId = tempEntity.clientMessageId,
                uploadUrl = presign.uploadUrl,
                file = prepared.file,
                mimeType = prepared.mimeType,
                onProgress = { progress ->
                    _uploadProgress.update { it + (tempEntity.clientMessageId to progress) }
                }
            )
            _uploadProgress.update { it - tempEntity.clientMessageId }
            if (uploadRes.isFailure) {
                dao.upsertMessage(tempEntity.copy(deliveryStatus = "FAILED"))
                return Result.failure(AppError.StorageTransferFailure)
            }

            val completeResp = apiService.completeUpload(CompleteUploadRequest(presign.mediaObjectId))
            if (!completeResp.isSuccessful) {
                dao.upsertMessage(tempEntity.copy(deliveryStatus = "FAILED"))
                return Result.failure(mapHttpError(completeResp))
            }

            val withMediaId = tempEntity.copy(mediaObjectId = presign.mediaObjectId)
            dao.upsertMessage(withMediaId)
            postMessageToServer(withMediaId, peerPublicKey)
        } catch (e: Exception) {
            _uploadProgress.update { it - tempEntity.clientMessageId }
            dao.upsertMessage(tempEntity.copy(deliveryStatus = "FAILED"))
            Result.failure(mapNetworkException(e))
        }
    }

    private suspend fun postMessageToServer(
        tempEntity: MessageEntity,
        peerPublicKey: String?
    ): Result<Unit> {
        return try {
            val resp = apiService.sendMessage(
                conversationId = tempEntity.conversationId,
                body = SendMessageRequest(
                    clientMessageId = tempEntity.clientMessageId,
                    messageType = tempEntity.messageType,
                    bodyText = tempEntity.bodyText,
                    isEncrypted = tempEntity.isEncrypted,
                    mediaObjectId = tempEntity.mediaObjectId,
                    mediaDurationMs = tempEntity.mediaDurationMs
                )
            )
            val dto = resp.body()
            if (resp.isSuccessful && dto != null) {
                val persisted = mapMessageToEntity(dto, peerPublicKey).copy(
                    localAttachmentUri = tempEntity.localAttachmentUri
                )
                dao.deleteTempMessageIfReplaced(tempEntity.clientMessageId, persisted.id)
                dao.upsertMessage(persisted)
                refreshConversations()
                Result.success(Unit)
            } else {
                dao.upsertMessage(tempEntity.copy(deliveryStatus = "FAILED"))
                Result.failure(mapHttpError(resp))
            }
        } catch (e: Exception) {
            dao.upsertMessage(tempEntity.copy(deliveryStatus = "FAILED"))
            Result.failure(mapNetworkException(e))
        }
    }

    private suspend fun handleWsEvent(event: WsEvent) {
        when (event) {
            is WsEvent.SyncRequired -> {
                refreshConversations()
            }
            is WsEvent.NewMessage -> {
                val msg = event.message
                val existing = dao.findMessage(msg.id, msg.clientMessageId)
                val conv = dao.observeConversation(msg.conversationId)
                // Decrypt using peer key if available
                val entity = mapMessageToEntity(msg, null)
                if (existing != null && existing.id != msg.id) {
                    dao.deleteTempMessageIfReplaced(msg.clientMessageId, msg.id)
                }
                dao.upsertMessage(
                    if (existing?.localAttachmentUri != null) {
                        entity.copy(localAttachmentUri = existing.localAttachmentUri)
                    } else entity
                )
                refreshConversations()

                val myId = preferencesManager.currentSession().userId
                if (msg.senderId != myId && activeConversationId != msg.conversationId) {
                    notificationManager.showPrivacySafeNewMessageNotification(
                        senderDisplayName = "My Chatting Pro",
                        conversationId = msg.conversationId
                    )
                }
            }
            is WsEvent.MessageReceipt -> {
                dao.updateMessageReceipt(
                    messageId = event.messageId,
                    status = event.status,
                    deliveredAt = event.deliveredAt,
                    readAt = event.readAt
                )
                refreshConversations()
            }
            is WsEvent.MessageDeleted -> {
                dao.markMessageDeleted(event.messageId, event.deletedAt)
                refreshConversations()
            }
            is WsEvent.PresenceUpdate -> {
                dao.updatePeerPresence(event.userId, event.isOnline, event.lastSeenAt)
            }
            is WsEvent.TypingUpdate -> {
                _typingPeers.update { it + (event.conversationId to event.isTyping) }
            }
            is WsEvent.CallSignal -> {
                // Handled directly by WebRtcCallManager
            }
        }
    }

    private suspend fun handleAuthResponse(response: Response<AuthResponse>): Result<AuthResponse> {
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            preferencesManager.saveSession(
                accessToken = body.accessToken,
                refreshToken = body.refreshToken,
                userId = body.user.id,
                deviceId = body.deviceId,
                phoneE164 = body.user.phoneE164,
                displayName = body.user.displayName,
                about = body.user.about,
                avatarUrl = body.user.avatarUrl
            )
            webSocketManager.start()
            Result.success(body)
        } else {
            Result.failure(mapHttpError(response))
        }
    }

    private suspend fun encryptIfPossible(
        plaintext: String,
        peerPublicKey: String?,
        conversationId: String
    ): Pair<String, Boolean> {
        if (peerPublicKey.isNullOrBlank()) return plaintext to false
        return try {
            val (_, wrappedPriv) = preferencesManager.getE2eeKeyPairRaw()
            if (wrappedPriv.isNullOrBlank()) return plaintext to false
            val cipher = e2eeManager.encryptText(plaintext, wrappedPriv, peerPublicKey, conversationId)
            cipher to true
        } catch (_: Exception) {
            plaintext to false
        }
    }

    private suspend fun decryptIfNeeded(
        bodyText: String?,
        isEncrypted: Boolean,
        peerPublicKey: String?,
        conversationId: String
    ): String? {
        if (bodyText == null) return null
        if (!isEncrypted || peerPublicKey.isNullOrBlank()) return bodyText
        val (_, wrappedPriv) = preferencesManager.getE2eeKeyPairRaw()
        if (wrappedPriv.isNullOrBlank()) return bodyText
        return e2eeManager.decryptText(bodyText, wrappedPriv, peerPublicKey, conversationId) ?: bodyText
    }

    private suspend fun mapConversationToEntity(dto: ConversationDto): ConversationEntity {
        val latest = dto.latestMessage
        val previewText = when {
            latest == null -> null
            latest.deletedAt != null -> null
            latest.messageType == "TEXT" -> decryptIfNeeded(
                latest.bodyText,
                latest.isEncrypted,
                dto.peer.devicePublicKey,
                dto.id
            )
            else -> latest.mediaFileName ?: "[${latest.messageType}]"
        }

        return ConversationEntity(
            id = dto.id,
            peerId = dto.peer.id,
            peerPhoneE164 = dto.peer.phoneE164,
            peerDisplayName = dto.peer.displayName,
            peerAbout = dto.peer.about,
            peerAvatarUrl = dto.peer.avatarUrl,
            peerPublicKey = dto.peer.devicePublicKey,
            peerIsOnline = dto.peer.isOnline,
            peerLastSeenAt = dto.peer.lastSeenAt,
            latestMessageText = previewText,
            latestMessageType = latest?.messageType,
            latestMessageStatus = latest?.deliveryStatus,
            latestMessageSenderId = latest?.senderId,
            latestMessageAt = latest?.createdAt ?: dto.updatedAt,
            unreadCount = dto.unreadCount,
            isBlockedByMe = dto.isBlockedByMe,
            hasBlockedMe = dto.hasBlockedMe,
            updatedAtMillis = DateTimeFormatterUtil.parseUtcTimestampMillis(dto.updatedAt)
        )
    }

    private suspend fun mapMessageToEntity(dto: MessageDto, peerPublicKey: String?): MessageEntity {
        val decrypted = if (dto.deletedAt != null) {
            null
        } else {
            decryptIfNeeded(dto.bodyText, dto.isEncrypted, peerPublicKey, dto.conversationId)
        }
        return MessageEntity(
            id = dto.id,
            clientMessageId = dto.clientMessageId,
            conversationId = dto.conversationId,
            senderId = dto.senderId,
            messageType = dto.messageType,
            bodyText = dto.bodyText,
            decryptedText = decrypted,
            isEncrypted = dto.isEncrypted,
            mediaObjectId = dto.mediaObjectId,
            mediaFileName = dto.mediaFileName,
            mediaMimeType = dto.mediaMimeType,
            mediaSizeBytes = dto.mediaSizeBytes,
            mediaDurationMs = dto.mediaDurationMs,
            localAttachmentUri = null,
            deliveryStatus = dto.deliveryStatus,
            createdAtIso = dto.createdAt,
            createdAtMillis = DateTimeFormatterUtil.parseUtcTimestampMillis(dto.createdAt),
            deliveredAtIso = dto.deliveredAt,
            readAtIso = dto.readAt,
            deletedAtIso = dto.deletedAt
        )
    }

    private fun mapHttpError(response: Response<*>): AppError {
        val parsed = networkClient.parseError(
            try { response.errorBody()?.string() } catch (_: Exception) { null }
        )
        return when (response.code()) {
            401 -> AppError.WrongPin
            404 -> AppError.AccountNotFound
            409 -> AppError.DuplicateAccount
            429 -> AppError.RateLimited(parsed?.retryAfterSeconds)
            502, 503, 504 -> AppError.BackendUnavailable
            else -> parsed?.error?.let { AppError.ServerMessage(it) } ?: AppError.Unknown
        }
    }

    private fun mapNetworkException(e: Exception): AppError {
        return when (e) {
            is UnknownHostException -> AppError.BackendUnavailable
            is SocketTimeoutException, is IOException -> AppError.NetworkFailure
            else -> AppError.Unknown
        }
    }

    private fun currentUtcIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
