package com.example.features.messages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.core.local.MessageEntity
import com.example.core.localization.DateTimeFormatterUtil
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocalAppStrings
import com.example.core.media.MediaCompressor
import com.example.core.media.VoicePlaybackState
import com.example.ui.components.AvatarView
import com.example.ui.components.DangerousFileDownloadDialog
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.components.ReportDialog
import com.example.ui.theme.LocalChatColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    viewModel: ChatDetailViewModel,
    onBack: () -> Unit,
    onStartCall: (peerId: String, peerName: String, isVideo: Boolean) -> Unit,
    onOpenMediaViewer: (messageId: String, mediaObjectId: String, localPath: String?, isVideo: Boolean, title: String) -> Unit
) {
    val strings = LocalAppStrings.current
    val language = LocalAppLanguage.current
    val chatColors = LocalChatColors.current
    val context = LocalContext.current

    val conversation by viewModel.conversation.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val myUserId by viewModel.myUserId.collectAsState()
    val composerText by viewModel.composerText.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val voiceRecording by viewModel.voiceRecordingState.collectAsState()
    val voicePlayback by viewModel.voicePlaybackState.collectAsState()
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
    val isLoadingOlder by viewModel.isLoadingOlder.collectAsState()
    val hasMoreOlder by viewModel.hasMoreOlder.collectAsState()
    val pendingPreview by viewModel.pendingPreview.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showAttachSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var selectedMessageForDetails by remember { mutableStateOf<MessageEntity?>(null) }
    var showReportUserDialog by remember { mutableStateOf(false) }
    var reportMessageTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var dangerousFileTarget by remember { mutableStateOf<MessageEntity?>(null) }

    // On-demand permissions with rationale
    var pendingPermissionAction by remember { mutableStateOf<String?>(null) }
    var rationaleText by remember { mutableStateOf<String?>(null) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            when (pendingPermissionAction) {
                "voice" -> viewModel.startVoiceRecording()
                "audio_call" -> conversation?.let { onStartCall(it.peerId, it.peerDisplayName, false) }
            }
        } else {
            // Show denied message via snackbar
            pendingPermissionAction = null
        }
    }

    val callCamMicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            conversation?.let { onStartCall(it.peerId, it.peerDisplayName, true) }
        }
    }

    // Zero-permission Photo Picker for Images and Videos
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.setPendingMediaPreview(it, isVideo = false) }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.setPendingMediaPreview(it, isVideo = true) }
    }

    // Zero-permission SAF Document picker for PDF, ZIP, APK, DOC/DOCX, audio, etc.
    val documentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.sendDocumentUri(it, strings) }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeBanner()
    }

    // Scroll to newest bottom when new message arrives near bottom
    val prevCount = remember { mutableStateOf(0) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && prevCount.value == 0) {
            listState.scrollToItem(messages.lastIndex)
        } else if (messages.size > prevCount.value && listState.firstVisibleItemIndex >= (prevCount.value - 5).coerceAtLeast(0)) {
            listState.animateScrollToItem(messages.lastIndex)
        }
        prevCount.value = messages.size
    }

    // Trigger upward pagination when scrolled to top
    val shouldLoadMore by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex <= 2 && hasMoreOlder && !isLoadingOlder && messages.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadOlderMessages()
    }

    fun requestVoiceRecord() {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.startVoiceRecording()
        } else {
            pendingPermissionAction = "voice"
            rationaleText = strings.micPermissionReason
        }
    }

    fun requestAudioCall() {
        val conv = conversation ?: return
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            onStartCall(conv.peerId, conv.peerDisplayName, false)
        } else {
            pendingPermissionAction = "audio_call"
            rationaleText = strings.micPermissionReason
        }
    }

    fun requestVideoCall() {
        val conv = conversation ?: return
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasMic && hasCam) {
            onStartCall(conv.peerId, conv.peerDisplayName, true)
        } else {
            pendingPermissionAction = "video_call"
            rationaleText = "${strings.cameraPermissionReason}\n\n${strings.micPermissionReason}"
        }
    }

    fun openFileSafely(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, file.name))
        } catch (_: Exception) {
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.close)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarView(
                            displayName = conversation?.peerDisplayName ?: "",
                            avatarUrl = conversation?.peerAvatarUrl,
                            size = 38.dp,
                            isOnline = conversation?.peerIsOnline == true,
                            showOnlineDot = true
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = conversation?.peerDisplayName ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = when {
                                isPeerTyping -> "..."
                                conversation?.peerIsOnline == true -> strings.onlineStatus
                                else -> strings.offlineStatus
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (conversation?.peerIsOnline == true) chatColors.onlineIndicator
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { requestAudioCall() },
                        enabled = conversation?.isBlockedByMe != true,
                        modifier = Modifier.testTag("start_audio_call_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = strings.audioCallTitle)
                    }
                    IconButton(
                        onClick = { requestVideoCall() },
                        enabled = conversation?.isBlockedByMe != true,
                        modifier = Modifier.testTag("start_video_call_button")
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = strings.videoCallTitle)
                    }
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("chat_safety_menu_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Safety")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                text = {
                                    Text(
                                        if (conversation?.isBlockedByMe == true) strings.unblockUser
                                        else strings.blockUser
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.toggleBlockPeer(strings)
                                },
                                modifier = Modifier.testTag("menu_block_user")
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                text = { Text(strings.reportUser) },
                                onClick = {
                                    showOverflowMenu = false
                                    showReportUserDialog = true
                                },
                                modifier = Modifier.testTag("menu_report_user")
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                if (conversation?.isBlockedByMe == true) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.userBlockedBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else if (voiceRecording.isRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.cancelVoiceRecording() }) {
                            Text(strings.voiceCancel, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${strings.recordingLabel} ${DateTimeFormatterUtil.formatDurationSeconds(voiceRecording.elapsedMs / 1000L)}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        FilledIconButton(
                            onClick = { viewModel.stopAndSendVoiceRecording(strings) },
                            modifier = Modifier.testTag("send_voice_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.voiceSend)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showAttachSheet = true },
                            modifier = Modifier.testTag("attach_media_button")
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = strings.attachMediaTitle)
                        }
                        OutlinedTextField(
                            value = composerText,
                            onValueChange = viewModel::updateComposerText,
                            placeholder = { Text(strings.messageInputHint) },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("message_text_input")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (composerText.isNotBlank()) {
                            FilledIconButton(
                                onClick = { viewModel.sendText(strings) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("send_message_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.sendButton)
                            }
                        } else {
                            FilledIconButton(
                                onClick = { requestVoiceRecord() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("record_voice_button")
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = strings.recordVoiceHint)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isLoadingOlder) {
                    item(key = "loading_older") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                    val currentDayKey = DateTimeFormatterUtil.localDayKey(msg.createdAtMillis)
                    val prevDayKey = if (index > 0) {
                        DateTimeFormatterUtil.localDayKey(messages[index - 1].createdAtMillis)
                    } else null

                    if (currentDayKey != prevDayKey) {
                        DateSeparatorHeader(
                            label = DateTimeFormatterUtil.formatDateSeparator(
                                msg.createdAtMillis,
                                language,
                                strings
                            )
                        )
                    }

                    val isMine = msg.senderId == myUserId
                    val activeUpload = uploadProgress[msg.clientMessageId]
                    val activeDownload = downloadProgress[msg.id]

                    MessageBubbleItem(
                        message = msg,
                        isMine = isMine,
                        uploadPercent = activeUpload,
                        downloadPercent = activeDownload,
                        voicePlayback = voicePlayback,
                        onLongPress = { selectedMessageForDetails = msg },
                        onRetry = { viewModel.retryMessage(msg, strings) },
                        onCancelUpload = { viewModel.cancelUpload(msg.clientMessageId) },
                        onTogglePlayVoice = { viewModel.togglePlayVoiceMessage(msg, strings) },
                        onOpenMedia = {
                            val mId = msg.mediaObjectId
                            if (mId != null || msg.localAttachmentUri != null) {
                                onOpenMediaViewer(
                                    msg.id,
                                    mId.orEmpty(),
                                    msg.localAttachmentUri,
                                    msg.messageType == "VIDEO",
                                    msg.mediaFileName ?: msg.messageType
                                )
                            }
                        },
                        onDownloadDocument = {
                            if (MediaCompressor.isDangerousFileType(msg.mediaFileName, msg.mediaMimeType)) {
                                dangerousFileTarget = msg
                            } else {
                                viewModel.downloadFileForOpen(msg, strings) { file, mime ->
                                    openFileSafely(file, mime)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Attachment Picker Bottom Sheet
    if (showAttachSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(strings.attachMediaTitle, style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        showAttachSheet = false
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("pick_image_button")
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.pickImage)
                }
                Button(
                    onClick = {
                        showAttachSheet = false
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("pick_video_button")
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.pickVideo)
                }
                Button(
                    onClick = {
                        showAttachSheet = false
                        documentPickerLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxWidth().testTag("pick_document_button")
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.pickDocument)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Image/Video Preview Confirmation Dialog before Upload
    pendingPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPendingMediaPreview() },
            title = { Text(if (preview.isVideo) strings.pickVideo else strings.pickImage) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!preview.isVideo) {
                        AsyncImage(
                            model = preview.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmAndSendPendingMedia(strings) },
                    modifier = Modifier.testTag("confirm_send_media_button")
                ) {
                    Text(strings.sendButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearPendingMediaPreview() }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Message Details Long-Press Bottom Sheet
    selectedMessageForDetails?.let { msg ->
        ModalBottomSheet(onDismissRequest = { selectedMessageForDetails = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(strings.messageDetailsTitle, style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()
                DetailRow(strings.messageContentLabel, msg.decryptedText ?: msg.mediaFileName ?: msg.messageType)
                DetailRow(strings.sentDateLabel, DateTimeFormatterUtil.formatLocalDate(msg.createdAtMillis, language))
                DetailRow(strings.sentTimeLabel, DateTimeFormatterUtil.formatLocalTime(msg.createdAtMillis, language))
                val deliveredLabel = msg.deliveredAtIso?.let {
                    DateTimeFormatterUtil.formatMessageTimestampLine(
                        DateTimeFormatterUtil.parseUtcTimestampMillis(it),
                        language,
                        strings
                    )
                } ?: strings.notAvailable
                DetailRow(strings.deliveredAtLabel, deliveredLabel)
                val readLabel = msg.readAtIso?.let {
                    DateTimeFormatterUtil.formatMessageTimestampLine(
                        DateTimeFormatterUtil.parseUtcTimestampMillis(it),
                        language,
                        strings
                    )
                } ?: strings.notAvailable
                DetailRow(strings.readAtLabel, readLabel)

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (msg.senderId == myUserId && msg.deletedAtIso == null && !msg.id.startsWith("local_")) {
                        TextButton(
                            onClick = {
                                val id = msg.id
                                selectedMessageForDetails = null
                                viewModel.deleteOwnMessage(id, strings)
                            },
                            modifier = Modifier.testTag("delete_own_message_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.deleteForMeOrEveryone, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(
                        onClick = {
                            reportMessageTarget = msg
                            selectedMessageForDetails = null
                        },
                        modifier = Modifier.testTag("report_message_button")
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.reportMessage)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showReportUserDialog) {
        ReportDialog(
            title = strings.reportUser,
            onSubmit = { reason ->
                showReportUserDialog = false
                viewModel.submitReport(null, reason, strings)
            },
            onDismiss = { showReportUserDialog = false }
        )
    }

    reportMessageTarget?.let { target ->
        ReportDialog(
            title = strings.reportMessage,
            onSubmit = { reason ->
                reportMessageTarget = null
                viewModel.submitReport(target.id, reason, strings)
            },
            onDismiss = { reportMessageTarget = null }
        )
    }

    dangerousFileTarget?.let { target ->
        DangerousFileDownloadDialog(
            fileName = target.mediaFileName ?: "attachment",
            onConfirmDownload = {
                dangerousFileTarget = null
                viewModel.downloadFileForOpen(target, strings) { file, mime ->
                    openFileSafely(file, mime)
                }
            },
            onDismiss = { dangerousFileTarget = null }
        )
    }

    rationaleText?.let { reason ->
        PermissionRationaleDialog(
            reasonText = reason,
            onConfirm = {
                val action = pendingPermissionAction
                rationaleText = null
                when (action) {
                    "voice", "audio_call" -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    "video_call" -> callCamMicLauncher.launch(
                        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                    )
                }
            },
            onDismiss = {
                rationaleText = null
                pendingPermissionAction = null
            }
        )
    }
}

@Composable
private fun DateSeparatorHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleItem(
    message: MessageEntity,
    isMine: Boolean,
    uploadPercent: Float?,
    downloadPercent: Float?,
    voicePlayback: VoicePlaybackState,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    onCancelUpload: () -> Unit,
    onTogglePlayVoice: () -> Unit,
    onOpenMedia: () -> Unit,
    onDownloadDocument: () -> Unit
) {
    val strings = LocalAppStrings.current
    val language = LocalAppLanguage.current
    val chatColors = LocalChatColors.current

    val bubbleColor = if (isMine) chatColors.outgoingBubble else chatColors.incomingBubble
    val contentColor = if (isMine) chatColors.onOutgoingBubble else chatColors.onIncomingBubble
    val timeText = remember(message.createdAtMillis, language) {
        DateTimeFormatterUtil.formatMessageTimestampLine(message.createdAtMillis, language, strings)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            tonalElevation = if (isMine) 0.dp else 2.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = {
                        when (message.messageType) {
                            "IMAGE", "VIDEO" -> onOpenMedia()
                            "VOICE", "AUDIO" -> onTogglePlayVoice()
                            "FILE" -> onDownloadDocument()
                        }
                    },
                    onLongClick = onLongPress
                )
                .testTag("message_bubble_${message.id}")
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                if (message.deletedAtIso != null) {
                    Text(
                        text = strings.messageDeleted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = contentColor.copy(alpha = 0.75f)
                    )
                } else {
                    when (message.messageType) {
                        "IMAGE" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!message.localAttachmentUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = File(message.localAttachmentUri),
                                        contentDescription = message.mediaFileName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = message.mediaFileName,
                                        modifier = Modifier.size(44.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        "VIDEO" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, tint = contentColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = message.mediaFileName ?: "Video",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = contentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = MediaCompressor.formatFileSize(message.mediaSizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = contentColor.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }

                        "VOICE", "AUDIO" -> {
                            val isPlayingThis = voicePlayback.activeMessageId == message.id && voicePlayback.isPlaying
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onTogglePlayVoice,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play voice",
                                        tint = contentColor
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    val durSec = (message.mediaDurationMs ?: 0L) / 1000L
                                    Text(
                                        text = if (message.messageType == "VOICE") "Voice ${DateTimeFormatterUtil.formatDurationSeconds(durSec)}"
                                        else (message.mediaFileName ?: "Audio"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = contentColor
                                    )
                                    if (voicePlayback.activeMessageId == message.id && voicePlayback.durationMs > 0) {
                                        LinearProgressIndicator(
                                            progress = { (voicePlayback.positionMs.toFloat() / voicePlayback.durationMs.toFloat()).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        "FILE" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = contentColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = message.mediaFileName ?: "Document",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = contentColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${message.mediaMimeType ?: ""} • ${MediaCompressor.formatFileSize(message.mediaSizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = contentColor.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        else -> {
                            Text(
                                text = message.decryptedText ?: message.bodyText.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor
                            )
                        }
                    }

                    if (uploadPercent != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { uploadPercent },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onCancelUpload, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = strings.cancel, tint = contentColor)
                            }
                        }
                    }

                    if (downloadPercent != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadPercent },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.isEncrypted) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = strings.e2eeVerifiedBadge,
                            tint = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.78f)
                    )
                    if (isMine) {
                        val (icon, tint) = when (message.deliveryStatus) {
                            "SENDING" -> Icons.Default.Schedule to contentColor.copy(alpha = 0.7f)
                            "SENT" -> Icons.Default.Done to contentColor.copy(alpha = 0.85f)
                            "DELIVERED" -> Icons.Default.DoneAll to contentColor.copy(alpha = 0.85f)
                            "SEEN" -> Icons.Default.DoneAll to chatColors.seenAccent
                            else -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = message.deliveryStatus,
                            tint = tint,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = isMine && message.deliveryStatus == "FAILED") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = strings.messageFailed,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("retry_message_button_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.retry, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
