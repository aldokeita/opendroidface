package com.opendroid.ai.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A TTS engine pronounces whatever it is handed, formatting included. These are
 * the shapes models actually produce.
 */
class SpeechTextTest {

    @Test
    fun `emphasis is spoken as the words, not the asterisks`() {
        assertEquals("Selesai!", SpeechText.forSpeech("**Selesai!**"))
        assertEquals("Selesai!", SpeechText.forSpeech("*Selesai!*"))
        assertEquals("Selesai!", SpeechText.forSpeech("***Selesai!***"))
        assertEquals("Selesai!", SpeechText.forSpeech("~~Selesai!~~"))
        assertEquals("penting sekali", SpeechText.forSpeech("_penting_ sekali"))
    }

    @Test
    fun `no markup character survives into the spoken text`() {
        val messy = "**Halo!** Aku sudah `buka` WhatsApp — lihat [di sini](https://x.co) 😊\n\n" +
            "# Ringkasan\n- langkah satu\n- langkah dua\n> catatan\n"
        val spoken = SpeechText.forSpeech(messy)

        listOf("*", "_", "`", "#", "~", "[", "](", "http").forEach { mark ->
            assertFalse("$mark in: $spoken", spoken.contains(mark))
        }
        assertTrue(spoken, spoken.contains("Halo!"))
        assertTrue(spoken, spoken.contains("buka"))
        assertTrue(spoken, spoken.contains("langkah satu"))
    }

    @Test
    fun `a link is read as its label, never as its address`() {
        // A bare URL is read character by character, which is unbearable.
        assertEquals("Buka dokumennya", SpeechText.forSpeech("Buka [dokumennya](https://example.com/a/b)"))
        assertEquals("Sudah kubuka", SpeechText.forSpeech("Sudah kubuka https://example.com/a/b"))
    }

    @Test
    fun `a list becomes a sentence rather than a row of dashes`() {
        val spoken = SpeechText.forSpeech("- satu\n- dua\n3. tiga")

        assertEquals("satu dua tiga", spoken)
    }

    @Test
    fun `plain speech is left exactly as written`() {
        val plain = "Alarmnya sudah kupasang untuk jam 6 sore."

        assertEquals(plain, SpeechText.forSpeech(plain))
    }

    @Test
    fun `arithmetic and prices are not mistaken for markup`() {
        assertEquals("Harganya 2500 rupiah, naik 5%.", SpeechText.forSpeech("Harganya 2500 rupiah, naik 5%."))
        assertEquals("Nilai tukar 1 USD ke IDR", SpeechText.forSpeech("Nilai tukar 1 USD ke IDR"))
    }

    @Test
    fun `text that was nothing but decoration says nothing`() {
        assertEquals("", SpeechText.forSpeech("😊"))
        assertEquals("", SpeechText.forSpeech("---"))
        assertEquals("", SpeechText.forSpeech("   "))
    }
}
