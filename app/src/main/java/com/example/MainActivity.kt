package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.core.localization.AppLanguage
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocalAppStrings
import com.example.core.localization.stringsFor
import com.example.ui.navigation.AppNavGraph
import com.example.ui.theme.MyChattingProTheme
import com.example.ui.theme.ThemePreference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MyChattingProApp).container

        setContent {
            val language by container.preferencesManager.languageFlow.collectAsState(initial = AppLanguage.ENGLISH)
            val themePreference by container.preferencesManager.themeFlow.collectAsState(initial = ThemePreference.SYSTEM)
            val strings = stringsFor(language)

            CompositionLocalProvider(
                LocalAppLanguage provides language,
                LocalAppStrings provides strings,
                LocalLayoutDirection provides language.layoutDirection
            ) {
                MyChattingProTheme(themePreference = themePreference) {
                    AppNavGraph(container = container)
                }
            }
        }
    }
}
