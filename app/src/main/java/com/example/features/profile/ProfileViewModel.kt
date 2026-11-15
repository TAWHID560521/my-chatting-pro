package com.example.features.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.local.PreferencesManager
import com.example.core.localization.AppStrings
import com.example.core.repository.ChatRepository
import com.example.ui.components.appErrorToLocalizedMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val displayName: String = "",
    val about: String = "",
    val phoneE164: String = "",
    val avatarUrl: String? = null,
    val selectedPhotoUri: Uri? = null,
    val fingerprint: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedSuccessfully: Boolean = false
)

class ProfileViewModel(
    private val repository: ChatRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val session = preferencesManager.currentSession()
            val identity = repository.getOrCreateDeviceIdentity()
            _uiState.value = _uiState.value.copy(
                displayName = session.displayName,
                about = session.about,
                phoneE164 = session.phoneE164,
                avatarUrl = session.avatarUrl,
                fingerprint = identity.fingerprint
            )
            repository.refreshMyProfile().onSuccess { user ->
                _uiState.value = _uiState.value.copy(
                    displayName = user.displayName,
                    about = user.about,
                    phoneE164 = user.phoneE164,
                    avatarUrl = user.avatarUrl
                )
            }
        }
    }

    fun updateDisplayName(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name, errorMessage = null, savedSuccessfully = false)
    }

    fun updateAbout(about: String) {
        _uiState.value = _uiState.value.copy(about = about, errorMessage = null, savedSuccessfully = false)
    }

    fun selectPhoto(uri: Uri) {
        _uiState.value = _uiState.value.copy(selectedPhotoUri = uri, errorMessage = null, savedSuccessfully = false)
    }

    fun saveProfile(strings: AppStrings) {
        val state = _uiState.value
        val name = state.displayName.trim()
        if (name.length < 2) {
            _uiState.value = state.copy(errorMessage = strings.errorDisplayNameRequired)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            val res = repository.updateProfile(
                displayName = name,
                about = state.about.trim(),
                newAvatarUri = state.selectedPhotoUri
            )
            res.fold(
                onSuccess = { user ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        displayName = user.displayName,
                        about = user.about,
                        avatarUrl = user.avatarUrl,
                        selectedPhotoUri = null,
                        savedSuccessfully = true
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = appErrorToLocalizedMessage(err, strings)
                    )
                }
            )
        }
    }

    class Factory(
        private val repository: ChatRepository,
        private val preferencesManager: PreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(repository, preferencesManager) as T
        }
    }
}
