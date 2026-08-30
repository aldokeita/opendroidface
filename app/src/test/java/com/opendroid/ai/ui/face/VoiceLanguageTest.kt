package com.opendroid.ai.ui.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLanguageTest {

    @Test
    fun `following the device is the first option`() {
        assertNull(VOICE_LANGUAGES.first().tag)
    }

    @Test
    fun `cycling walks the list and wraps back to the device`() {
        var tag = VOICE_LANGUAGES.first().tag
        val seen = mutableListOf(tag)
        repeat(VOICE_LANGUAGES.size - 1) {
            tag = nextVoiceLanguage(tag)
            seen.add(tag)
        }
        assertEquals(VOICE_LANGUAGES.map { it.tag }, seen)
        assertNull(nextVoiceLanguage(tag))
    }

    @Test
    fun `an unknown tag cycles back into the list instead of getting stuck`() {
        // A tag left over from an older build must not trap the chip.
        assertEquals(VOICE_LANGUAGES[0].tag, nextVoiceLanguage("xx-XX"))
    }

    @Test
    fun `labels exist for every offered language`() {
        VOICE_LANGUAGES.forEach { assertTrue(it.label.isNotBlank()) }
        assertEquals("Indonesia", voiceLanguageLabel("id-ID"))
        assertEquals(VOICE_LANGUAGES.first().label, voiceLanguageLabel(null))
    }

    @Test
    fun `an unlisted tag still gets a readable label`() {
        assertTrue(voiceLanguageLabel("fr-FR").isNotBlank())
    }
}
