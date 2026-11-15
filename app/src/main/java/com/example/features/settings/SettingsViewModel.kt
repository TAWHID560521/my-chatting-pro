package com.example.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.local.PreferencesManager
import com.example.core.local.UserSession
import com.example.core.localization.AppLanguage
import com.example.core.localization.AppStrings
import com.example.core.repository.ChatRepository
import com.example.ui.components.appErrorToLocalizedMessage
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: ChatRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val session: StateFlow<UserSession> = preferencesManager.sessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSession())

    val language: StateFlow<AppLanguage> = preferencesManager.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.ENGLISH)

    val themePreference: StateFlow<ThemePreference> = preferencesManager.themeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreference.SYSTEM)

    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { preferencesManager.setLanguage(lang) }
    }

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { preferencesManager.setTheme(theme) }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            onLoggedOut()
        }
    }

    fun deleteAccount(
        pin: String,
        strings: AppStrings,
        onDeleted: () -> Unit
    ) {
        if (pin.length != 5) {
            _statusMessage.value = strings.errorInvalidPin
            return
        }
        viewModelScope.launch {
            _isDeletingAccount.value = true
            val res = repository.deleteAccount(pin)
            _isDeletingAccount.value = false
            res.fold(
                onSuccess = {
                    onDeleted()
                },
                onFailure = { err ->
                    _statusMessage.value = appErrorToLocalizedMessage(err, strings)
                }
            )
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    class Factory(
        private val repository: ChatRepository,
        private val preferencesManager: PreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository, preferencesManager) as T
        }
    }
}
