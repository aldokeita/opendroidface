package com.opendroid.ai.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SpokenLanguageTest {

    private val english = Locale.forLanguageTag("en-US")

    @Test
    fun `an explicit choice wins over both the device and the text`() {
        assertEquals(
            SpokenLanguage.INDONESIAN.language,
            SpokenLanguage.localeFor("This is an English sentence.", "id-ID", english).language
        )
        assertEquals(
            "en",
            SpokenLanguage.localeFor("Alarmnya sudah saya pasang.", "en-US", english).language
        )
    }

    @Test
    fun `an Indonesian answer on an English phone is spoken in Indonesian`() {
        val locale = SpokenLanguage.localeFor(
            "Baik, alarmnya sudah saya pasang untuk jam enam sore.",
            preferredTag = null,
            deviceLocale = english
        )

        assertEquals("id", locale.language)
    }

    @Test
    fun `an English answer on an English phone stays on the device language`() {
        val locale = SpokenLanguage.localeFor(
            "I have set the alarm for six in the evening.",
            preferredTag = null,
            deviceLocale = english
        )

        assertEquals(english, locale)
    }

    @Test
    fun `text with no evidence follows the device rather than guessing`() {
        assertEquals(english, SpokenLanguage.localeFor("OK.", null, english))
        assertEquals(english, SpokenLanguage.localeFor("", null, english))
        assertEquals(english, SpokenLanguage.localeFor("Spotify, WhatsApp, Gmail", null, english))
    }

    @Test
    fun `a word that survives in English needs company`() {
        // "Meeting di Zoom" is a sentence an English speaker in Jakarta says.
        assertFalse(SpokenLanguage.looksIndonesian("Meeting di Zoom"))
        assertTrue(SpokenLanguage.looksIndonesian("Meeting di Zoom sudah kubatalkan"))
    }

    @Test
    fun `a two-word command is enough on its own`() {
        // The commands this app exists to receive are short. Requiring two
        // markers made every one of them look like English.
        assertTrue(SpokenLanguage.looksIndonesian("buka whatsapp"))
        assertTrue(SpokenLanguage.looksIndonesian("nyalakan senter"))
        assertTrue(SpokenLanguage.looksIndonesian("Kirim pesan ke Budi"))
        assertFalse(SpokenLanguage.looksIndonesian("open whatsapp"))
    }

    @Test
    fun `a short command picks the Indonesian voice`() {
        assertEquals("id", SpokenLanguage.localeFor("buka whatsapp", null, english).language)
        assertEquals(english, SpokenLanguage.localeFor("open whatsapp", null, english))
    }

    @Test
    fun `an Indonesian device is followed without inspecting the text`() {
        val indonesianPhone = Locale.forLanguageTag("id-ID")

        assertEquals(
            indonesianPhone,
            SpokenLanguage.localeFor("Playing your music now.", null, indonesianPhone)
        )
    }
}
