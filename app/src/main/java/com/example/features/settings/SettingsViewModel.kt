package com.example.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.local.PreferencesManager
import com.example.core.local.UserSession
import com.example.core.localization.AppLanguage
import com.example.core.localization.AppStrings
import com.example.core.model.ActiveSessionDto
import com.example.core.repository.ChatRepository
import com.example.ui.components.appErrorToLocalizedMessage
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveDevicesUiState(
    val isLoading: Boolean = false,
    val isRevoking: Boolean = false,
    val isTogglingOtp: Boolean = false,
    val emailMasked: String? = null,
    val emailVerified: Boolean = false,
    val requireLoginOtp: Boolean = true,
    val sessions: List<ActiveSessionDto> = emptyList(),
    val bindEmailInput: String = "",
    val bindEmailOtpInput: String = "",
    val bindEmailChallengeId: String? = null,
    val bindEmailMasked: String? = null,
    val bindEmailCooldownSeconds: Int = 0,
    val isBindingEmail: Boolean = false,
    val bindEmailDialogError: String? = null
)

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

    private val _devicesState = MutableStateFlow(ActiveDevicesUiState())
    val devicesState: StateFlow<ActiveDevicesUiState> = _devicesState.asStateFlow()

    private var bindEmailCooldownJob: Job? = null

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { preferencesManager.setLanguage(lang) }
    }

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { preferencesManager.setTheme(theme) }
    }

    fun loadActiveSessions(strings: AppStrings) {
        viewModelScope.launch {
            _devicesState.value = _devicesState.value.copy(isLoading = true)
            val res = repository.getActiveSessions()
            res.fold(
                onSuccess = { body ->
                    _devicesState.value = _devicesState.value.copy(
                        isLoading = false,
                        emailMasked = body.emailMasked,
                        emailVerified = body.emailVerified,
                        requireLoginOtp = body.requireLoginOtp,
                        sessions = body.sessions
                    )
                },
                onFailure = { err ->
                    _devicesState.value = _devicesState.value.copy(isLoading = false)
                    _statusMessage.value = appErrorToLocalizedMessage(err, strings)
                }
            )
        }
    }

    fun revokeSession(sessionId: String, strings: AppStrings, onCurrentDeviceRevoked: () -> Unit = {}) {
        viewModelScope.launch {
            _devicesState.value = _devicesState.value.copy(isRevoking = true)
            val res = repository.revokeSession(sessionId)
            res.fold(
                onSuccess = { body ->
                    _devicesState.value = _devicesState.value.copy(
                        isRevoking = false,
                        sessions = _devicesState.value.sessions.filterNot { it.sessionId == sessionId }
                    )
                    if (body.wasCurrentDevice) {
                        onCurrentDeviceRevoked()
                    } else {
                        _statusMessage.value = strings.deviceLoggedOutSuccess
                    }
                },
                onFailure = { err ->
                    _devicesState.value = _devicesState.value.copy(isRevoking = false)
                    _statusMessage.value = appErrorToLocalizedMessage(err, strings)
                }
            )
        }
    }

    fun revokeOtherSessions(strings: AppStrings) {
        viewModelScope.launch {
            _devicesState.value = _devicesState.value.copy(isRevoking = true)
            val res = repository.revokeOtherSessions()
            res.fold(
                onSuccess = {
                    _devicesState.value = _devicesState.value.copy(
                        isRevoking = false,
                        sessions = _devicesState.value.sessions.filter { it.isCurrent }
                    )
                    _statusMessage.value = strings.logoutAllOthersSuccess
                },
                onFailure = { err ->
                    _devicesState.value = _devicesState.value.copy(isRevoking = false)
                    _statusMessage.value = appErrorToLocalizedMessage(err, strings)
                }
            )
        }
    }

    fun toggleLoginOtp(enabled: Boolean, strings: AppStrings) {
        val prev = _devicesState.value.requireLoginOtp
        _devicesState.value = _devicesState.value.copy(isTogglingOtp = true, requireLoginOtp = enabled)
        viewModelScope.launch {
            val res = repository.setLoginOtpRequired(enabled)
            res.fold(
                onSuccess = {
                    _devicesState.value = _devicesState.value.copy(isTogglingOtp = false)
                },
                onFailure = { err ->
                    _devicesState.value = _devicesState.value.copy(
                        isTogglingOtp = false,
                        requireLoginOtp = prev
                    )
                    _statusMessage.value = appErrorToLocalizedMessage(err, strings)
                }
            )
        }
    }

    fun updateBindEmailInput(value: String) {
        _devicesState.value = _devicesState.value.copy(
            bindEmailInput = value,
            bindEmailDialogError = null
        )
    }

    fun updateBindEmailOtpInput(value: String) {
        val digits = value.filter { it.isDigit() }.take(6)
        _devicesState.value = _devicesState.value.copy(
            bindEmailOtpInput = digits,
            bindEmailDialogError = null
        )
    }

    fun resetBindEmailDialog() {
        bindEmailCooldownJob?.cancel()
        _devicesState.value = _devicesState.value.copy(
            bindEmailInput = "",
            bindEmailOtpInput = "",
            bindEmailChallengeId = null,
            bindEmailMasked = null,
            bindEmailCooldownSeconds = 0,
            isBindingEmail = false,
            bindEmailDialogError = null
        )
    }

    fun requestBindEmailOtp(strings: AppStrings) {
        val email = _devicesState.value.bindEmailInput.trim()
        if (!EMAIL_REGEX.matches(email)) {
            _devicesState.value = _devicesState.value.copy(bindEmailDialogError = strings.errorInvalidEmail)
            return
        }
        viewModelScope.launch {
            _devicesState.value = _devicesState.value.copy(isBindingEmail = true, bindEmailDialogError = null)
            val res = repository.requestBindEmailOtp(email)
            res.fold(
                onSuccess = { resp ->
                    _devicesState.value = _devicesState.value.copy(
                        isBindingEmail = false,
                        bindEmailChallengeId = resp.challengeId,
                        bindEmailMasked = resp.maskedEmail,
                        bindEmailOtpInput = ""
                    )
                    startBindEmailCooldown()
                },
                onFailure = { err ->
                    _devicesState.value = _devicesState.value.copy(
                        isBindingEmail = false,
                        bindEmailDialogError = appErrorToLocalizedMessage(err, strings)
                    )
                }
            )
        }
    }

    fun verifyBindEmailOtp(strings: AppStrings, onVerified: () -> Unit) {
        val st = _devicesState.value
        val challengeId = st.bindEmailChallengeId
        if (challengeId.isNullOrBlank() || st.bindEmailOtpInput.length != 6) {
            _devicesState.value = st.copy(bindEmailDialogError = strings.errorInvalidOtp)
            return
        }
        viewModelScope.launch {
            _devicesState.value = _devicesState.value.copy(isBindingEmail = true, bindEmailDialogError = null)
            val res = repository.verifyBindEmailOtp(st.bindEmailInput.trim(), challengeId, st.bindEmailOtpInput)
            res.fold(
                onSuccess = { resp ->
                    bindEmailCooldownJob?.cancel()
                    _devicesState.value = _devicesState.value.copy(
                        isBindingEmail = false,
                        emailMasked = resp.emailMasked,
                        emailVerified = resp.emailVerified,
                        bindEmailChallengeId = null,
                        bindEmailOtpInput = "",
                        bindEmailCooldownSeconds = 0
                    )
                    onVerified()
                },
                onFailure = { err ->
                    _devicesState.value = _devicesState.value.copy(
                        isBindingEmail = false,
                        bindEmailDialogError = appErrorToLocalizedMessage(err, strings)
                    )
                }
            )
        }
    }

    private fun startBindEmailCooldown(seconds: Int = 60) {
        bindEmailCooldownJob?.cancel()
        bindEmailCooldownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining >= 0) {
                _devicesState.value = _devicesState.value.copy(bindEmailCooldownSeconds = remaining)
                if (remaining == 0) break
                delay(1000L)
                remaining -= 1
            }
        }
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

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
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
