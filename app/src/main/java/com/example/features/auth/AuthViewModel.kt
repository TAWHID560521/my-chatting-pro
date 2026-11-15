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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val selectedCountry: CountryItem = CountriesData.byIso("BD"),
    val phoneInput: String = "",
    val emailInput: String = "",
    val pinInput: String = "",
    val confirmPinInput: String = "",
    val displayNameInput: String = "",
    val otpInput: String = "",
    val otpChallengeId: String? = null,
    val maskedEmail: String? = null,
    val otpCooldownSeconds: Int = 0,
    val requiresLoginOtpStep: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val suspiciousNotice: String? = null,
    val recoveryInfo: RecoveryInfoResponse? = null,
    val isRecoveryLoading: Boolean = false,
    val recoveryStep: Int = 0,
    val recoveryOtpCode: String = "",
    val recoveryChallengeId: String? = null,
    val recoveryMaskedEmail: String? = null,
    val recoveryNewPin: String = "",
    val recoveryConfirmPin: String = "",
    val recoverySuccessMessage: String? = null
)

class AuthViewModel(
    private val repository: ChatRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val recentCountryIsos: StateFlow<List<String>> = preferencesManager.recentCountriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var cooldownJob: Job? = null

    fun selectCountry(country: CountryItem) {
        _uiState.value = _uiState.value.copy(selectedCountry = country, errorMessage = null)
        viewModelScope.launch {
            preferencesManager.recordRecentCountry(country.isoCode)
        }
    }

    fun updatePhone(value: String) {
        _uiState.value = _uiState.value.copy(phoneInput = value, errorMessage = null)
    }

    fun updateEmail(value: String) {
        _uiState.value = _uiState.value.copy(emailInput = value, errorMessage = null)
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

    fun updateOtp(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(6)
        _uiState.value = _uiState.value.copy(otpInput = digitsOnly, errorMessage = null)
    }

    fun updateRecoveryOtp(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(6)
        _uiState.value = _uiState.value.copy(recoveryOtpCode = digitsOnly, errorMessage = null)
    }

    fun updateRecoveryNewPin(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(5)
        _uiState.value = _uiState.value.copy(recoveryNewPin = digitsOnly, errorMessage = null)
    }

    fun updateRecoveryConfirmPin(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(5)
        _uiState.value = _uiState.value.copy(recoveryConfirmPin = digitsOnly, errorMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearLoginOtpStep() {
        _uiState.value = _uiState.value.copy(
            requiresLoginOtpStep = false,
            otpInput = "",
            otpChallengeId = null,
            maskedEmail = null,
            errorMessage = null
        )
    }

    fun resetRecoveryFlow() {
        _uiState.value = _uiState.value.copy(
            recoveryStep = 0,
            recoveryOtpCode = "",
            recoveryChallengeId = null,
            recoveryMaskedEmail = null,
            recoveryNewPin = "",
            recoveryConfirmPin = "",
            recoverySuccessMessage = null,
            errorMessage = null
        )
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

    fun validateNameAndEmailStep(strings: AppStrings): Boolean {
        val state = _uiState.value
        val name = state.displayNameInput.trim()
        if (name.length < 2) {
            _uiState.value = state.copy(errorMessage = strings.errorDisplayNameRequired)
            return false
        }
        if (!isValidEmail(state.emailInput)) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidEmail)
            return false
        }
        _uiState.value = state.copy(errorMessage = null)
        return true
    }

    fun sendRegisterOtp(strings: AppStrings, onOtpSent: () -> Unit) {
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
        if (!isValidEmail(state.emailInput)) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidEmail)
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
            val result = repository.requestRegisterOtp(e164, state.emailInput.trim())
            result.fold(
                onSuccess = { resp ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        otpChallengeId = resp.challengeId,
                        maskedEmail = resp.maskedEmail,
                        otpInput = ""
                    )
                    startOtpCooldown()
                    onOtpSent()
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

    fun resendRegisterOtp(strings: AppStrings) {
        val state = _uiState.value
        if (state.otpCooldownSeconds > 0 || state.isLoading) return
        val e164 = CountriesData.normalizeToE164(state.selectedCountry, state.phoneInput) ?: return
        if (!isValidEmail(state.emailInput)) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidEmail)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.requestRegisterOtp(e164, state.emailInput.trim())
            result.fold(
                onSuccess = { resp ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        otpChallengeId = resp.challengeId,
                        maskedEmail = resp.maskedEmail,
                        otpInput = ""
                    )
                    startOtpCooldown()
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

    fun verifyRegisterOtpAndCreate(strings: AppStrings, onSuccess: () -> Unit) {
        val state = _uiState.value
        val e164 = CountriesData.normalizeToE164(state.selectedCountry, state.phoneInput)
        if (e164 == null) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidPhone)
            return
        }
        val challengeId = state.otpChallengeId
        if (challengeId.isNullOrBlank() || state.otpInput.length != 6) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidOtp)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.register(
                phoneE164 = e164,
                email = state.emailInput.trim(),
                otpChallengeId = challengeId,
                otpCode = state.otpInput,
                pin = state.pinInput,
                displayName = state.displayNameInput.trim()
            )
            result.fold(
                onSuccess = {
                    cooldownJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        pinInput = "",
                        confirmPinInput = "",
                        otpInput = "",
                        otpChallengeId = null,
                        otpCooldownSeconds = 0
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
                    if (auth.requiresOtp && !auth.challengeId.isNullOrBlank()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            requiresLoginOtpStep = true,
                            otpChallengeId = auth.challengeId,
                            maskedEmail = auth.maskedEmail,
                            otpInput = ""
                        )
                        startOtpCooldown()
                    } else {
                        cooldownJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            pinInput = "",
                            requiresLoginOtpStep = false,
                            otpChallengeId = null,
                            otpInput = "",
                            suspiciousNotice = auth.suspiciousLoginNotice
                        )
                        onSuccess()
                    }
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

    fun verifyLoginOtp(strings: AppStrings, onSuccess: () -> Unit) {
        val state = _uiState.value
        val e164 = CountriesData.normalizeToE164(state.selectedCountry, state.phoneInput)
        if (e164 == null) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidPhone)
            return
        }
        val challengeId = state.otpChallengeId
        if (challengeId.isNullOrBlank() || state.otpInput.length != 6) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidOtp)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.login(
                phoneE164 = e164,
                pin = state.pinInput,
                otpChallengeId = challengeId,
                otpCode = state.otpInput
            )
            result.fold(
                onSuccess = { auth ->
                    if (auth.requiresOtp && !auth.challengeId.isNullOrBlank()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            otpChallengeId = auth.challengeId,
                            maskedEmail = auth.maskedEmail,
                            errorMessage = strings.errorInvalidOtp
                        )
                    } else {
                        cooldownJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            pinInput = "",
                            otpInput = "",
                            otpChallengeId = null,
                            maskedEmail = null,
                            requiresLoginOtpStep = false,
                            otpCooldownSeconds = 0,
                            suspiciousNotice = auth.suspiciousLoginNotice
                        )
                        onSuccess()
                    }
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

    fun resendLoginOtp(strings: AppStrings) {
        val state = _uiState.value
        if (state.otpCooldownSeconds > 0 || state.isLoading) return
        val e164 = CountriesData.normalizeToE164(state.selectedCountry, state.phoneInput) ?: return
        if (state.pinInput.length != 5) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.login(e164, state.pinInput)
            result.fold(
                onSuccess = { auth ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        otpChallengeId = auth.challengeId ?: _uiState.value.otpChallengeId,
                        maskedEmail = auth.maskedEmail ?: _uiState.value.maskedEmail,
                        otpInput = ""
                    )
                    startOtpCooldown()
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

    fun requestRecoveryOtp(strings: AppStrings) {
        val state = _uiState.value
        val e164 = CountriesData.normalizeToE164(state.selectedCountry, state.phoneInput)
        if (e164 == null) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidPhone)
            return
        }
        if (!isValidEmail(state.emailInput)) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidEmail)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.requestRecoveryOtp(e164, state.emailInput.trim())
            result.fold(
                onSuccess = { resp ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        recoveryStep = 1,
                        recoveryChallengeId = resp.challengeId,
                        recoveryMaskedEmail = resp.maskedEmail,
                        recoveryOtpCode = ""
                    )
                    startOtpCooldown()
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

    fun resetPinWithRecoveryOtp(strings: AppStrings) {
        val state = _uiState.value
        val e164 = CountriesData.normalizeToE164(state.selectedCountry, state.phoneInput)
        if (e164 == null) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidPhone)
            return
        }
        val challengeId = state.recoveryChallengeId
        if (challengeId.isNullOrBlank() || state.recoveryOtpCode.length != 6) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidOtp)
            return
        }
        if (state.recoveryNewPin.length != 5) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidPin)
            return
        }
        if (state.recoveryNewPin != state.recoveryConfirmPin) {
            _uiState.value = state.copy(errorMessage = strings.errorPinMismatch)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.resetPinWithOtp(
                phoneE164 = e164,
                email = state.emailInput.trim(),
                otpChallengeId = challengeId,
                otpCode = state.recoveryOtpCode,
                newPin = state.recoveryNewPin
            )
            result.fold(
                onSuccess = {
                    cooldownJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        recoveryStep = 2,
                        recoverySuccessMessage = strings.resetPinSuccessMsg,
                        recoveryOtpCode = "",
                        recoveryNewPin = "",
                        recoveryConfirmPin = "",
                        pinInput = "",
                        otpCooldownSeconds = 0
                    )
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

    private fun startOtpCooldown(seconds: Int = 60) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining >= 0) {
                _uiState.value = _uiState.value.copy(otpCooldownSeconds = remaining)
                if (remaining == 0) break
                delay(1000L)
                remaining -= 1
            }
        }
    }

    private fun isValidEmail(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.length in 5..254 && EMAIL_REGEX.matches(trimmed)
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
            return AuthViewModel(repository, preferencesManager) as T
        }
    }
}
