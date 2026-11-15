package com.example.core.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.config.AppConfig
import com.example.core.localization.AppLanguage
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "my_chatting_pro_prefs")

data class UserSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val deviceId: String = "",
    val phoneE164: String = "",
    val displayName: String = "",
    val about: String = "",
    val avatarUrl: String? = null
) {
    val isLoggedIn: Boolean get() = accessToken.isNotBlank() && userId.isNotBlank()
}

class PreferencesManager(private val context: Context) {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val PHONE_E164 = stringPreferencesKey("phone_e164")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val ABOUT = stringPreferencesKey("about")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val LANGUAGE = stringPreferencesKey("language_code")
        val THEME = stringPreferencesKey("theme_mode")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val RECENT_COUNTRIES = stringPreferencesKey("recent_countries")
        val E2EE_PUB = stringPreferencesKey("e2ee_public_key")
        val E2EE_PRIV_WRAPPED = stringPreferencesKey("e2ee_wrapped_private_key")
        val WS_CURSOR = stringPreferencesKey("ws_last_cursor")
    }

    val sessionFlow: Flow<UserSession> = context.dataStore.data.map { prefs ->
        UserSession(
            accessToken = prefs[Keys.ACCESS_TOKEN].orEmpty(),
            refreshToken = prefs[Keys.REFRESH_TOKEN].orEmpty(),
            userId = prefs[Keys.USER_ID].orEmpty(),
            deviceId = prefs[Keys.DEVICE_ID].orEmpty(),
            phoneE164 = prefs[Keys.PHONE_E164].orEmpty(),
            displayName = prefs[Keys.DISPLAY_NAME].orEmpty(),
            about = prefs[Keys.ABOUT].orEmpty(),
            avatarUrl = prefs[Keys.AVATAR_URL]
        )
    }

    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[Keys.LANGUAGE])
    }

    val themeFlow: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.THEME]) {
            ThemePreference.LIGHT.name -> ThemePreference.LIGHT
            ThemePreference.DARK.name -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
    }

    val apiBaseUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.API_BASE_URL]?.takeIf { it.isNotBlank() } ?: AppConfig.defaultApiBaseUrl
    }

    val recentCountriesFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.RECENT_COUNTRIES]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    suspend fun currentSession(): UserSession = sessionFlow.first()
    suspend fun currentApiBaseUrl(): String = apiBaseUrlFlow.first()
    suspend fun currentWsCursor(): String? = context.dataStore.data.first()[Keys.WS_CURSOR]

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        deviceId: String,
        phoneE164: String,
        displayName: String,
        about: String,
        avatarUrl: String?
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.USER_ID] = userId
            prefs[Keys.DEVICE_ID] = deviceId
            prefs[Keys.PHONE_E164] = phoneE164
            prefs[Keys.DISPLAY_NAME] = displayName
            prefs[Keys.ABOUT] = about
            if (avatarUrl != null) prefs[Keys.AVATAR_URL] = avatarUrl else prefs.remove(Keys.AVATAR_URL)
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun updateProfileCache(displayName: String, about: String, avatarUrl: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = displayName
            prefs[Keys.ABOUT] = about
            if (avatarUrl != null) prefs[Keys.AVATAR_URL] = avatarUrl else prefs.remove(Keys.AVATAR_URL)
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setApiBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            val clean = url.trim().trimEnd('/')
            if (clean.isBlank()) prefs.remove(Keys.API_BASE_URL) else prefs[Keys.API_BASE_URL] = clean
        }
    }

    suspend fun recordRecentCountry(isoCode: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_COUNTRIES]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableList()
                ?: mutableListOf()
            current.removeAll { it.equals(isoCode, ignoreCase = true) }
            current.add(0, isoCode.uppercase())
            prefs[Keys.RECENT_COUNTRIES] = current.take(5).joinToString(",")
        }
    }

    suspend fun saveWsCursor(cursor: String) {
        context.dataStore.edit { it[Keys.WS_CURSOR] = cursor }
    }

    suspend fun getE2eeKeyPairRaw(): Pair<String?, String?> {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.E2EE_PUB] to prefs[Keys.E2EE_PRIV_WRAPPED]
    }

    suspend fun saveE2eeKeyPairRaw(publicB64: String, wrappedPrivateB64: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.E2EE_PUB] = publicB64
            prefs[Keys.E2EE_PRIV_WRAPPED] = wrappedPrivateB64
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.DEVICE_ID)
            prefs.remove(Keys.PHONE_E164)
            prefs.remove(Keys.DISPLAY_NAME)
            prefs.remove(Keys.ABOUT)
            prefs.remove(Keys.AVATAR_URL)
            prefs.remove(Keys.WS_CURSOR)
        }
    }
}
