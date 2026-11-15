package com.example

import android.app.Application
import com.example.core.calls.WebRtcCallManager
import com.example.core.crypto.E2eeManager
import com.example.core.local.AppDatabase
import com.example.core.local.PreferencesManager
import com.example.core.media.VoicePlayerManager
import com.example.core.media.VoiceRecorderManager
import com.example.core.network.NetworkClient
import com.example.core.network.S3MediaTransferClient
import com.example.core.network.WebSocketManager
import com.example.core.notifications.AppNotificationManager
import com.example.core.repository.ChatRepository

class AppContainer(application: Application) {
    val preferencesManager = PreferencesManager(application)
    val database = AppDatabase.getInstance(application)
    val networkClient = NetworkClient(preferencesManager)
    val notificationManager = AppNotificationManager(application).apply { ensureChannels() }
    val webSocketManager = WebSocketManager(application, preferencesManager, networkClient.moshi)
    val s3TransferClient = S3MediaTransferClient(application, networkClient.mediaTransferOkHttpClient)
    val e2eeManager = E2eeManager()
    val voiceRecorderManager = VoiceRecorderManager(application)
    val voicePlayerManager = VoicePlayerManager()

    val chatRepository = ChatRepository(
        context = application,
        preferencesManager = preferencesManager,
        database = database,
        networkClient = networkClient,
        apiService = networkClient.apiService,
        webSocketManager = webSocketManager,
        s3TransferClient = s3TransferClient,
        e2eeManager = e2eeManager,
        notificationManager = notificationManager
    )

    val webRtcCallManager = WebRtcCallManager(
        context = application,
        apiService = networkClient.apiService,
        webSocketManager = webSocketManager,
        notificationManager = notificationManager
    )
}

class MyChattingProApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
