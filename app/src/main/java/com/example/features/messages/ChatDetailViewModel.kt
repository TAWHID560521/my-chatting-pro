package com.example.features.messages

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.local.ConversationEntity
import com.example.core.local.MessageEntity
import com.example.core.local.PreferencesManager
import com.example.core.localization.AppStrings
import com.example.core.media.MediaCompressor
import com.example.core.media.PreparedMediaFile
import com.example.core.media.VoicePlaybackState
import com.example.core.media.VoicePlayerManager
import com.example.core.media.VoiceRecorderManager
import com.example.core.media.VoiceRecordingState
import com.example.core.network.WebSocketManager
import com.example.core.repository.ChatRepository
import com.example.ui.components.appErrorToLocalizedMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class PendingAttachmentPreview(
    val uri: Uri,
    val isVideo: Boolean
)

class ChatDetailViewModel(
    private val appContext: Context,
    private val conversationId: String,
    private val repository: ChatRepository,
    private val preferencesManager: PreferencesManager,
    private val webSocketManager: WebSocketManager,
    private val voiceRecorderManager: VoiceRecorderManager,
    private val voicePlayerManager: VoicePlayerManager
) : ViewModel() {

    val conversation: StateFlow<ConversationEntity?> = repository.observeConversation(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<MessageEntity>> = repository.observeMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uploadProgress: StateFlow<Map<String, Float>> = repository.uploadProgress
    val voiceRecordingState: StateFlow<VoiceRecordingState> = voiceRecorderManager.state
    val voicePlaybackState: StateFlow<VoicePlaybackState> = voicePlayerManager.state

    val isPeerTyping: StateFlow<Boolean> = repository.typingPeers
        .map { it[conversationId] == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _myUserId = MutableStateFlow("")
    val myUserId: StateFlow<String> = _myUserId.asStateFlow()

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder.asStateFlow()

    private val _hasMoreOlder = MutableStateFlow(true)
    val hasMoreOlder: StateFlow<Boolean> = _hasMoreOlder.asStateFlow()

    private val _nextCursor = MutableStateFlow<String?>(null)

    private val _pendingPreview = MutableStateFlow<PendingAttachmentPreview?>(null)
    val pendingPreview: StateFlow<PendingAttachmentPreview?> = _pendingPreview.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var typingStopJob: Job? = null

    init {
        repository.activeConversationId = conversationId
        viewModelScope.launch {
            _myUserId.value = preferencesManager.currentSession().userId
            repository.markConversationRead(conversationId)
            loadLatestPage()
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (repository.activeConversationId == conversationId) {
            repository.activeConversationId = null
        }
        voiceRecorderManager.cancelRecording()
        voicePlayerManager.stop()
    }

    private suspend fun loadLatestPage() {
        val peerKey = conversation.value?.peerPublicKey
        val res = repository.loadMessagesPage(conversationId, cursor = null, peerPublicKey = peerKey)
        res.onSuccess { (cursor, hasMore) ->
            _nextCursor.value = cursor
            _hasMoreOlder.value = hasMore
        }
    }

    fun loadOlderMessages() {
        if (_isLoadingOlder.value || !_hasMoreOlder.value) return
        val cursor = _nextCursor.value ?: return
        viewModelScope.launch {
            _isLoadingOlder.value = true
            val res = repository.loadMessagesPage(
                conversationId = conversationId,
                cursor = cursor,
                peerPublicKey = conversation.value?.peerPublicKey
            )
            res.onSuccess { (next, hasMore) ->
                _nextCursor.value = next
                _hasMoreOlder.value = hasMore
            }
            _isLoadingOlder.value = false
        }
    }

    fun updateComposerText(text: String) {
        _composerText.value = text
        webSocketManager.sendTyping(conversationId, text.isNotBlank())
        typingStopJob?.cancel()
        if (text.isNotBlank()) {
            typingStopJob = viewModelScope.launch {
                delay(2500L)
                webSocketManager.sendTyping(conversationId, false)
            }
        }
    }

    fun sendText(strings: AppStrings) {
        val text = _composerText.value.trim()
        if (text.isEmpty()) return
        _composerText.value = ""
        webSocketManager.sendTyping(conversationId, false)

        viewModelScope.launch {
            val res = repository.sendTextMessage(
                conversationId = conversationId,
                plaintext = text,
                peerPublicKey = conversation.value?.peerPublicKey
            )
            if (res.isFailure) {
                _statusBanner.value = appErrorToLocalizedMessage(res.exceptionOrNull()!!, strings)
            }
        }
    }

    fun setPendingMediaPreview(uri: Uri, isVideo: Boolean) {
        _pendingPreview.value = PendingAttachmentPreview(uri, isVideo)
    }

    fun clearPendingMediaPreview() {
        _pendingPreview.value = null
    }

    fun confirmAndSendPendingMedia(strings: AppStrings) {
        val preview = _pendingPreview.value ?: return
        _pendingPreview.value = null
        viewModelScope.launch {
            val preparedRes = if (preview.isVideo) {
                MediaCompressor.copyUriToTempFile(appContext, preview.uri)
            } else {
                MediaCompressor.compressImageUri(appContext, preview.uri)
            }
            val prepared = preparedRes.getOrElse {
                _statusBanner.value = strings.errorStorageTransfer
                return@launch
            }
            val msgType = if (preview.isVideo) "VIDEO" else "IMAGE"
            val category = if (preview.isVideo) "videos" else "images"
            val res = repository.sendMediaMessage(
                conversationId = conversationId,
                messageType = msgType,
                mediaCategory = category,
                preparedFile = prepared,
                durationMs = null,
                peerPublicKey = conversation.value?.peerPublicKey
            )
            if (res.isFailure) {
                _statusBanner.value = appErrorToLocalizedMessage(res.exceptionOrNull()!!, strings)
            }
        }
    }

    fun sendDocumentUri(uri: Uri, strings: AppStrings) {
        viewModelScope.launch {
            val prepared = MediaCompressor.copyUriToTempFile(appContext, uri).getOrElse {
                _statusBanner.value = strings.errorStorageTransfer
                return@launch
            }
            val isAudio = prepared.mimeType.startsWith("audio/")
            val msgType = if (isAudio) "AUDIO" else "FILE"
            val category = if (isAudio) "audio" else "files"
            val res = repository.sendMediaMessage(
                conversationId = conversationId,
                messageType = msgType,
                mediaCategory = category,
                preparedFile = prepared,
                durationMs = null,
                peerPublicKey = conversation.value?.peerPublicKey
            )
            if (res.isFailure) {
                _statusBanner.value = appErrorToLocalizedMessage(res.exceptionOrNull()!!, strings)
            }
        }
    }

    fun startVoiceRecording(): Boolean = voiceRecorderManager.startRecording()

    fun cancelVoiceRecording() = voiceRecorderManager.cancelRecording()

    fun stopAndSendVoiceRecording(strings: AppStrings) {
        val durationMs = voiceRecorderManager.lastDurationMs()
        val prepared: PreparedMediaFile = voiceRecorderManager.stopAndCollect() ?: return
        viewModelScope.launch {
            val res = repository.sendMediaMessage(
                conversationId = conversationId,
                messageType = "VOICE",
                mediaCategory = "audio",
                preparedFile = prepared,
                durationMs = durationMs,
                peerPublicKey = conversation.value?.peerPublicKey
            )
            if (res.isFailure) {
                _statusBanner.value = appErrorToLocalizedMessage(res.exceptionOrNull()!!, strings)
            }
        }
    }

    fun togglePlayVoiceMessage(message: MessageEntity, strings: AppStrings) {
        val localPath = message.localAttachmentUri
        if (!localPath.isNullOrBlank() && File(localPath).exists()) {
            voicePlayerManager.togglePlay(message.id, File(localPath))
            return
        }
        val mediaId = message.mediaObjectId ?: return
        val fileName = message.mediaFileName ?: "voice_${message.id}.m4a"
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (message.id to 0.05f)
            val res = repository.downloadMediaToSafeFile(mediaId, fileName) { prog ->
                _downloadProgress.value = _downloadProgress.value + (message.id to prog)
            }
            _downloadProgress.value = _downloadProgress.value - message.id
            res.fold(
                onSuccess = { file -> voicePlayerManager.togglePlay(message.id, file) },
                onFailure = { err -> _statusBanner.value = appErrorToLocalizedMessage(err, strings) }
            )
        }
    }

    fun downloadFileForOpen(
        message: MessageEntity,
        strings: AppStrings,
        onDownloaded: (File, String) -> Unit
    ) {
        val localPath = message.localAttachmentUri
        if (!localPath.isNullOrBlank() && File(localPath).exists()) {
            onDownloaded(File(localPath), message.mediaMimeType ?: "application/octet-stream")
            return
        }
        val mediaId = message.mediaObjectId ?: return
        val fileName = message.mediaFileName ?: "attachment_${message.id}"
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (message.id to 0.05f)
            val res = repository.downloadMediaToSafeFile(mediaId, fileName) { prog ->
                _downloadProgress.value = _downloadProgress.value + (message.id to prog)
            }
            _downloadProgress.value = _downloadProgress.value - message.id
            res.fold(
                onSuccess = { file ->
                    onDownloaded(file, message.mediaMimeType ?: "application/octet-stream")
                },
                onFailure = { err ->
                    _statusBanner.value = appErrorToLocalizedMessage(err, strings)
                }
            )
        }
    }

    fun cancelUpload(clientMessageId: String) {
        repository.cancelUpload(clientMessageId)
    }

    fun retryMessage(message: MessageEntity, strings: AppStrings) {
        viewModelScope.launch {
            val res = repository.retryFailedMessage(message, conversation.value?.peerPublicKey)
            if (res.isFailure) {
                _statusBanner.value = appErrorToLocalizedMessage(res.exceptionOrNull()!!, strings)
            }
        }
    }

    fun deleteOwnMessage(messageId: String, strings: AppStrings) {
        viewModelScope.launch {
            val res = repository.deleteOwnMessage(messageId)
            if (res.isFailure) {
                _statusBanner.value = appErrorToLocalizedMessage(res.exceptionOrNull()!!, strings)
            }
        }
    }

    fun toggleBlockPeer(strings: AppStrings) {
        val conv = conversation.value ?: return
        viewModelScope.launch {
            val res = repository.setUserBlocked(conv.peerId, !conv.isBlockedByMe)
            if (res.isFailure) {
                _statusBanner.value = appErrorToLocalizedMessage(res.exceptionOrNull()!!, strings)
            }
        }
    }

    fun submitReport(messageId: String?, reason: String, strings: AppStrings) {
        val peerId = conversation.value?.peerId
        viewModelScope.launch {
            val res = repository.submitReport(
                targetUserId = if (messageId == null) peerId else null,
                messageId = messageId,
                reason = reason
            )
            _statusBanner.value = if (res.isSuccess) {
                strings.reportSubmitted
            } else {
                appErrorToLocalizedMessage(res.exceptionOrNull()!!, strings)
            }
        }
    }

    fun consumeBanner() {
        _statusBanner.value = null
    }

    class Factory(
        private val appContext: Context,
        private val conversationId: String,
        private val repository: ChatRepository,
        private val preferencesManager: PreferencesManager,
        private val webSocketManager: WebSocketManager,
        private val voiceRecorderManager: VoiceRecorderManager,
        private val voicePlayerManager: VoicePlayerManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatDetailViewModel(
                appContext,
                conversationId,
                repository,
                preferencesManager,
                webSocketManager,
                voiceRecorderManager,
                voicePlayerManager
            ) as T
        }
    }
}
