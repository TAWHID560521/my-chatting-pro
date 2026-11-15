package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches My Chatting Pro and dynamic version is readable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("My Chatting Pro", appName)

        val versionInfo = AppConfig.readPackageVersionInfo(context)
        assertTrue(versionInfo.versionName.isNotBlank())
        assertTrue(versionInfo.versionCode >= 1L)
    }
}
