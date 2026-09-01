package com.opendroid.ai.core.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppLanguageTest {

    private val englishPhone = Locale.forLanguageTag("en-US")
    private val indonesianPhone = Locale.forLanguageTag("id-ID")

    @Test
    fun `an explicit choice does not consult the words at all`() {
        // This is the point of the setting. Guessing from the text meant the
        // same request could be answered either way depending on its wording.
        assertTrue(AppLanguage.INDONESIAN.wantsIndonesian("open whatsapp", englishPhone))
        assertFalse(AppLanguage.ENGLISH.wantsIndonesian("buka whatsapp", indonesianPhone))
    }

    @Test
    fun `following the device takes the device first`() {
        assertTrue(AppLanguage.SYSTEM.wantsIndonesian("open whatsapp", indonesianPhone))
    }

    @Test
    fun `following an English device still falls back to the words`() {
        // A phone set to English used by someone speaking Indonesian is the
        // common case here, and the words are the only evidence available.
        assertTrue(AppLanguage.SYSTEM.wantsIndonesian("buka whatsapp", englishPhone))
        assertFalse(AppLanguage.SYSTEM.wantsIndonesian("open whatsapp", englishPhone))
    }

    @Test
    fun `only an explicit choice pins the speaking language`() {
        assertEquals("id-ID", AppLanguage.INDONESIAN.speechTag())
        assertEquals("en-US", AppLanguage.ENGLISH.speechTag())
        assertNull(AppLanguage.SYSTEM.speechTag())
    }

    @Test
    fun `an unknown stored id falls back to following the device`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromId(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromId("klingon"))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromId("id"))
    }
}
