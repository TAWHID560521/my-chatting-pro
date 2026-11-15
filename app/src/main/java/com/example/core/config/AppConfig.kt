package com.example.core.config

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.BuildConfig

object AppConfig {
    const val DEVELOPER_OWNER_NAME = "TAWHID SHAKH"
    const val WEBSITE_URL = "https://sites.google.com/view/you-pr/%E0%A6%B9%E0%A6%AE"
    const val YOUTUBE_URL = "https://www.youtube.com/channel/UClRY5eaWzQbXG2y2E5xcYdg"
    const val FACEBOOK_URL = "https://www.facebook.com/profile.php?id=61584997594783"
    const val FEEDBACK_EMAIL = "hafeztawhid15@gmail.com"
    const val PRIVACY_POLICY_URL = "https://sites.google.com/view/your-wab/%E0%A6%B9%E0%A6%AE"

    val defaultApiBaseUrl: String
        get() = BuildConfig.API_BASE_URL.trimEnd('/')

    val defaultWsBaseUrl: String
        get() = BuildConfig.WS_BASE_URL.trimEnd('/')

    fun deriveWsUrl(apiBaseUrl: String): String {
        val clean = apiBaseUrl.trim().trimEnd('/')
        return when {
            clean.startsWith("https://", ignoreCase = true) ->
                "wss://" + clean.removePrefix("https://") + "/ws"
            clean.startsWith("http://", ignoreCase = true) ->
                "ws://" + clean.removePrefix("http://") + "/ws"
            else -> defaultWsBaseUrl
        }
    }

    data class AppVersionInfo(
        val versionName: String,
        val versionCode: Long
    )

    fun readPackageVersionInfo(context: Context): AppVersionInfo {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            AppVersionInfo(
                versionName = info.versionName ?: BuildConfig.VERSION_NAME,
                versionCode = code
            )
        } catch (_: Exception) {
            AppVersionInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong()
            )
        }
    }
}
