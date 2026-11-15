package com.example.features.addchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.data.CountriesData
import com.example.core.data.CountryItem
import com.example.core.local.ConversationEntity
import com.example.core.local.PreferencesManager
import com.example.core.localization.AppStrings
import com.example.core.model.UserProfileDto
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

data class AddChatUiState(
    val selectedCountry: CountryItem = CountriesData.byIso("BD"),
    val nameFilterInput: String = "",
    val phoneInput: String = "",
    val isSearching: Boolean = false,
    val isStartingChat: Boolean = false,
    val hasSearched: Boolean = false,
    val foundUser: UserProfileDto? = null,
    val errorMessage: String? = null
)

class AddChatViewModel(
    private val repository: ChatRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddChatUiState())
    val uiState: StateFlow<AddChatUiState> = _uiState.asStateFlow()

    val recentCountries: StateFlow<List<String>> = preferencesManager.recentCountriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var debounceJob: Job? = null

    fun selectCountry(country: CountryItem) {
        _uiState.value = _uiState.value.copy(
            selectedCountry = country,
            hasSearched = false,
            foundUser = null,
            errorMessage = null
        )
        viewModelScope.launch { preferencesManager.recordRecentCountry(country.isoCode) }
    }

    fun updateNameFilter(name: String) {
        _uiState.value = _uiState.value.copy(nameFilterInput = name, errorMessage = null)
    }

    fun updatePhone(phone: String, strings: AppStrings) {
        _uiState.value = _uiState.value.copy(
            phoneInput = phone,
            hasSearched = false,
            foundUser = null,
            errorMessage = null
        )
        // Debounced search when valid E.164 is entered
        debounceJob?.cancel()
        val e164 = CountriesData.normalizeToE164(_uiState.value.selectedCountry, phone)
        if (e164 != null) {
            debounceJob = viewModelScope.launch {
                delay(550L)
                executeSearch(e164, _uiState.value.nameFilterInput, strings)
            }
        }
    }

    fun searchUserExplicit(strings: AppStrings) {
        debounceJob?.cancel()
        val state = _uiState.value
        val e164 = CountriesData.normalizeToE164(state.selectedCountry, state.phoneInput)
        if (e164 == null) {
            _uiState.value = state.copy(errorMessage = strings.errorInvalidPhone)
            return
        }
        viewModelScope.launch {
            executeSearch(e164, state.nameFilterInput, strings)
        }
    }

    private suspend fun executeSearch(e164: String, nameFilter: String, strings: AppStrings) {
        _uiState.value = _uiState.value.copy(isSearching = true, errorMessage = null)
        val res = repository.searchUser(e164, nameFilter)
        res.fold(
            onSuccess = { body ->
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    hasSearched = true,
                    foundUser = if (body.found) body.user else null
                )
            },
            onFailure = { err ->
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    hasSearched = false,
                    foundUser = null,
                    errorMessage = appErrorToLocalizedMessage(err, strings)
                )
            }
        )
    }

    fun startChat(strings: AppStrings, onConversationReady: (ConversationEntity) -> Unit) {
        val target = _uiState.value.foundUser ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isStartingChat = true, errorMessage = null)
            val res = repository.createOrGetConversation(target.id)
            res.fold(
                onSuccess = { conv ->
                    _uiState.value = _uiState.value.copy(isStartingChat = false)
                    onConversationReady(conv)
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isStartingChat = false,
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
            return AddChatViewModel(repository, preferencesManager) as T
        }
    }
}
