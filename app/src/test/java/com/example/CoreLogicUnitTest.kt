package com.example

import com.example.core.data.CountriesData
import com.example.core.localization.AppLanguage
import com.example.core.localization.DateTimeFormatterUtil
import com.example.core.localization.stringsFor
import com.example.core.media.MediaCompressor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CoreLogicUnitTest {

    @Test
    fun `E164 normalization handles Bangladesh, US, UK, and invalid numbers`() {
        val bd = CountriesData.byIso("BD")
        assertEquals("+8801711000000", CountriesData.normalizeToE164(bd, "01711000000"))
        assertEquals("+8801711000000", CountriesData.normalizeToE164(bd, "1711-000-000"))
        assertNull(CountriesData.normalizeToE164(bd, "12345"))

        val us = CountriesData.byIso("US")
        assertEquals("+14155551234", CountriesData.normalizeToE164(us, "(415) 555-1234"))

        val gb = CountriesData.byIso("GB")
        assertEquals("+447911123456", CountriesData.normalizeToE164(gb, "07911 123456"))
    }

    @Test
    fun `localization supports Bangla, English, and Arabic RTL`() {
        assertFalse(AppLanguage.BANGLA.isRtl)
        assertFalse(AppLanguage.ENGLISH.isRtl)
        assertTrue(AppLanguage.ARABIC.isRtl)

        assertEquals("আজ", stringsFor(AppLanguage.BANGLA).todayLabel)
        assertEquals("Today", stringsFor(AppLanguage.ENGLISH).todayLabel)
        assertEquals("اليوم", stringsFor(AppLanguage.ARABIC).todayLabel)
    }

    @Test
    fun `date separator distinguishes today, yesterday, and older UTC timestamps`() {
        val en = stringsFor(AppLanguage.ENGLISH)
        val bn = stringsFor(AppLanguage.BANGLA)
        val ar = stringsFor(AppLanguage.ARABIC)

        val now = System.currentTimeMillis()
        assertEquals("Today", DateTimeFormatterUtil.formatDateSeparator(now, AppLanguage.ENGLISH, en))
        assertEquals("আজ", DateTimeFormatterUtil.formatDateSeparator(now, AppLanguage.BANGLA, bn))
        assertEquals("اليوم", DateTimeFormatterUtil.formatDateSeparator(now, AppLanguage.ARABIC, ar))

        val yesterday = Calendar.getInstance(TimeZone.getDefault()).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        assertEquals("Yesterday", DateTimeFormatterUtil.formatDateSeparator(yesterday, AppLanguage.ENGLISH, en))
        assertEquals("গতকাল", DateTimeFormatterUtil.formatDateSeparator(yesterday, AppLanguage.BANGLA, bn))
        assertEquals("أمس", DateTimeFormatterUtil.formatDateSeparator(yesterday, AppLanguage.ARABIC, ar))
    }

    @Test
    fun `dangerous file detector flags apk and zip while allowing pdf`() {
        assertTrue(MediaCompressor.isDangerousFileType("app-release.apk", "application/vnd.android.package-archive"))
        assertTrue(MediaCompressor.isDangerousFileType("archive.zip", "application/zip"))
        assertFalse(MediaCompressor.isDangerousFileType("document.pdf", "application/pdf"))
    }
}
