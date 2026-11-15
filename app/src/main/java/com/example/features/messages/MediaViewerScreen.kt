package com.example.features.messages

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.core.localization.LocalAppStrings
import com.example.core.repository.ChatRepository
import com.example.ui.components.appErrorToLocalizedMessage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    repository: ChatRepository,
    mediaObjectId: String,
    localPath: String?,
    isVideo: Boolean,
    title: String,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mediaObjectId, localPath) {
        if (!localPath.isNullOrBlank() && File(localPath).exists()) {
            resolvedUrl = Uri.fromFile(File(localPath)).toString()
        } else if (mediaObjectId.isNotBlank()) {
            val res = repository.getPresignedDownloadUrl(mediaObjectId)
            res.fold(
                onSuccess = { url -> resolvedUrl = url },
                onFailure = { err -> errorMessage = appErrorToLocalizedMessage(err, strings) }
            )
        } else {
            errorMessage = strings.errorStorageTransfer
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.85f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("media_viewer_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.close)
                    }
                }
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                resolvedUrl == null -> {
                    CircularProgressIndicator(color = Color.White)
                }
                isVideo -> {
                    val url = resolvedUrl!!
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoURI(Uri.parse(url))
                                setOnPreparedListener { mp ->
                                    mp.start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    AsyncImage(
                        model = resolvedUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
