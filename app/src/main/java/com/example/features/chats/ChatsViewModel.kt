package com.example.features.chats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.local.ConversationEntity
import com.example.core.local.PreferencesManager
import com.example.core.local.UserSession
import com.example.core.localization.AppStrings
import com.example.core.media.MediaCompressor
import com.example.core.network.WebSocketManager
import com.example.core.network.WsConnectionState
import com.example.core.repository.ChatRepository
import com.example.ui.components.appErrorToLocalizedMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatsViewModel(
    private val appContext: Context,
    private val repository: ChatRepository,
    private val preferencesManager: PreferencesManager,
    private val webSocketManager: WebSocketManager
) : ViewModel() {

    val conversations: StateFlow<List<ConversationEntity>> = repository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val session: StateFlow<UserSession> = preferencesManager.sessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSession())

    val wsState: StateFlow<WsConnectionState> = webSocketManager.connectionState
    val isNetworkAvailable: StateFlow<Boolean> = webSocketManager.isNetworkAvailable

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _bannerError = MutableStateFlow<String?>(null)
    val bannerError: StateFlow<String?> = _bannerError.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        webSocketManager.start()
        refresh(null)
    }

    fun refresh(strings: AppStrings?) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _bannerError.value = null
            val res = repository.refreshConversations()
            if (res.isFailure && strings != null) {
                _bannerError.value = appErrorToLocalizedMessage(res.exceptionOrNull()!!, strings)
            }
            _isRefreshing.value = false
        }
    }

    fun clearTemporaryCache(strings: AppStrings) {
        viewModelScope.launch {
            MediaCompressor.clearTemporaryCacheOnly(appContext)
            _toastMessage.value = strings.clearCacheSuccessMessage
        }
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    class Factory(
        private val appContext: Context,
        private val repository: ChatRepository,
        private val preferencesManager: PreferencesManager,
        private val webSocketManager: WebSocketManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatsViewModel(appContext, repository, preferencesManager, webSocketManager) as T
        }
    }
}
