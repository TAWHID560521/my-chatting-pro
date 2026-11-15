package com.example.core.localization

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Converts server-side canonical UTC ISO-8601 TIMESTAMPTZ strings into the user's
 * current local device timezone, formatted according to selected language (bn/en/ar).
 * Works cleanly across Android API 24+ without desugaring.
 */
object DateTimeFormatterUtil {
    private val utcZone: TimeZone = TimeZone.getTimeZone("UTC")

    private val isoFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX"
    )

    fun parseUtcTimestampMillis(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        for (pattern in isoFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = utcZone
                val parsed = sdf.parse(isoString)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // try next format
            }
        }
        return System.currentTimeMillis()
    }

    fun formatLocalTime(epochMillis: Long, language: AppLanguage): String {
        val sdf = SimpleDateFormat("h:mm a", language.locale)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(epochMillis))
    }

    fun formatLocalDate(epochMillis: Long, language: AppLanguage): String {
        val pattern = when (language) {
            AppLanguage.BANGLA -> "d MMMM yyyy"
            AppLanguage.ENGLISH -> "d MMM yyyy"
            AppLanguage.ARABIC -> "d MMMM yyyy"
        }
        val sdf = SimpleDateFormat(pattern, language.locale)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(epochMillis))
    }

    fun formatDateSeparator(epochMillis: Long, language: AppLanguage, strings: AppStrings): String {
        val localZone = TimeZone.getDefault()
        val targetCal = Calendar.getInstance(localZone).apply { timeInMillis = epochMillis }
        val todayCal = Calendar.getInstance(localZone)
        val yesterdayCal = Calendar.getInstance(localZone).apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(targetCal, todayCal) -> strings.todayLabel
            isSameDay(targetCal, yesterdayCal) -> strings.yesterdayLabel
            else -> formatLocalDate(epochMillis, language)
        }
    }

    fun formatChatListTime(epochMillis: Long, language: AppLanguage, strings: AppStrings): String {
        val localZone = TimeZone.getDefault()
        val targetCal = Calendar.getInstance(localZone).apply { timeInMillis = epochMillis }
        val todayCal = Calendar.getInstance(localZone)
        val yesterdayCal = Calendar.getInstance(localZone).apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(targetCal, todayCal) -> formatLocalTime(epochMillis, language)
            isSameDay(targetCal, yesterdayCal) -> strings.yesterdayLabel
            else -> {
                val sdf = SimpleDateFormat("d MMM", language.locale)
                sdf.timeZone = localZone
                sdf.format(Date(epochMillis))
            }
        }
    }

    fun formatMessageTimestampLine(epochMillis: Long, language: AppLanguage, strings: AppStrings): String {
        val localZone = TimeZone.getDefault()
        val targetCal = Calendar.getInstance(localZone).apply { timeInMillis = epochMillis }
        val todayCal = Calendar.getInstance(localZone)
        val yesterdayCal = Calendar.getInstance(localZone).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val time = formatLocalTime(epochMillis, language)

        return when {
            isSameDay(targetCal, todayCal) -> time
            isSameDay(targetCal, yesterdayCal) -> "${strings.yesterdayLabel}, $time"
            else -> "${formatLocalDate(epochMillis, language)}, $time"
        }
    }

    fun localDayKey(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(epochMillis))
    }

    fun formatDurationSeconds(totalSeconds: Long): String {
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
