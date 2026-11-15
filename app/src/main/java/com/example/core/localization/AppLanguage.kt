package com.example.core.localization

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

enum class AppLanguage(
    val code: String,
    val nativeLabel: String,
    val locale: Locale,
    val isRtl: Boolean
) {
    BANGLA("bn", "বাংলা", Locale("bn", "BD"), false),
    ENGLISH("en", "English", Locale.ENGLISH, false),
    ARABIC("ar", "العربية", Locale("ar", "SA"), true);

    val layoutDirection: LayoutDirection
        get() = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }
val LocalAppStrings = staticCompositionLocalOf { EnglishStrings }
