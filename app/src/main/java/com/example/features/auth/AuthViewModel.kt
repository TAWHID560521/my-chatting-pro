package com.example.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.data.CountriesData
import com.example.core.data.CountryItem
import com.example.core.local.PreferencesManager
import com.example.core.localization.AppLanguage
import com.example.core.localization.AppStrings
import com.example.core.model.RecoveryInfoResponse
import com.example.core.repository.ChatRepository
import com.example.ui.components.appErrorToLocalizedMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val selectedCountry: CountryItem = CountriesData.byIso("BD"),
    val phoneInput: String = "",
    val pinInput: String = "",
    val confirmPinInput: String = "",
    val displayNameInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val suspiciousNotice: String? = null,
    val recoveryInfo: RecoveryInfoResponse? = null,
    val isRecoveryLoading: Boolean = false
)

class AuthViewModel(
    private val repository: ChatRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val recentCountryIsos: StateFlow<List<String>> = preferencesManager.recentCountriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCountry(country: CountryItem) {
        _uiState.value = _uiState.value.copy(selectedCountry = country, errorMessage = null)
        viewModelScope.launch {
            preferencesManager.recordRecentCountry(country.isoCode)
        }
    }

    fun updatePhone(value: String) {
        _uiState.value = _uiState.value.copy(phoneInput = value, errorMessage = null)
    }

    fun updatePin(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(5)
        _uiState.value = _uiState.value.copy(pinInput = digitsOnly, errorMessage = null)
    }

    fun updateConfirmPin(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(5)
        _uiState.value = _uiState.value.copy(confirmPinInput = digitsOnly, errorMessage = null)
    }

    fun updateDisplayName(value: String) {
        _uiState.value = _uiState.value.copy(displayNameInput = value, errorMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { preferencesManager.setLanguage(language) }
    }

    fun formattedPhonePreview(): String {
        val state = _uiState.value
        return CountriesData.normalizeToE164(state.selectedCountry, state.phoneInput)
            ?: "${state.selectedCountry.callingCode} ${state.phoneInput.trim()}"
    }

    fun validatePhoneStep(strings: AppStrings): Boolean {
        val state = _uiState.value
        val e164 = CountriesData.normalizeToE164(state.selectedCountry, state.phoneInput)
        if (e164 == null) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidPhone)
            return false
        }
        _uiState.value = state.copy(errorMessage = null)
        return true
    }

    fun validateNameStep(strings: AppStrings): Boolean {
        val state = _uiState.value
        val name = state.displayNameInput.trim()
        if (name.length < 2) {
            _uiState.value = state.copy(errorMessage = strings.errorDisplayNameRequired)
            return false
        }
        _uiState.value = state.copy(errorMessage = null)
        return true
    }

    fun login(strings: AppStrings, onSuccess: () -> Unit) {
        val state = _uiState.value
        val e164 = CountriesData.normalizeToE164(state.selectedCountry, state.phoneInput)
        if (e164 == null) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidPhone)
            return
        }
        if (state.pinInput.length != 5) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidPin)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.login(e164, state.pinInput)
            result.fold(
                onSuccess = { auth ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        pinInput = "",
                        suspiciousNotice = auth.suspiciousLoginNotice
                    )
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = appErrorToLocalizedMessage(err, strings)
                    )
                }
            )
        }
    }

    fun register(strings: AppStrings, onSuccess: () -> Unit) {
        val state = _uiState.value
        val e164 = CountriesData.normalizeToE164(state.selectedCountry, state.phoneInput)
        if (e164 == null) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidPhone)
            return
        }
        val name = state.displayNameInput.trim()
        if (name.length < 2) {
            _uiState.value = state.copy(errorMessage = strings.errorDisplayNameRequired)
            return
        }
        if (state.pinInput.length != 5) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidPin)
            return
        }
        if (state.pinInput != state.confirmPinInput) {
            _uiState.value = state.copy(errorMessage = strings.errorPinMismatch)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.register(e164, state.pinInput, name)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        pinInput = "",
                        confirmPinInput = ""
                    )
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = appErrorToLocalizedMessage(err, strings)
                    )
                }
            )
        }
    }

    fun loadRecoveryInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecoveryLoading = true)
            val res = repository.fetchRecoveryInfo()
            _uiState.value = _uiState.value.copy(
                isRecoveryLoading = false,
                recoveryInfo = res.getOrNull()
            )
        }
    }

    class Factory(
        private val repository: ChatRepository,
        private val preferencesManager: PreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(repository, preferencesManager) as T
        }
    }
}
