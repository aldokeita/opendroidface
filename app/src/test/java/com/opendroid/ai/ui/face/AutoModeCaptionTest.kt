package com.opendroid.ai.ui.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hands-free is a conversation held out loud. The caption is optional, so what
 * it does when it is off matters as much as what it says when it is on.
 */
class AutoModeCaptionTest {

    @Test
    fun `the agent's answer is typed out, the user's own speech is not`() {
        val reply = captionFor(null, "", "Alarmnya sudah kupasang.", isListening = false, showTranscript = true)
        assertEquals("Alarmnya sudah kupasang.", reply.text)
        assertTrue(reply.typed)

        // Recognition already arrives a word at a time; typing it again would
        // trail behind the person's own voice.
        val speech = captionFor(null, "pasang alarm jam", "", isListening = true, showTranscript = true)
        assertEquals("pasang alarm jam", speech.text)
        assertFalse(speech.typed)
    }

    @Test
    fun `an open microphone takes the line from the previous answer`() {
        val caption = captionFor(
            errorMessage = null,
            transcript = "berapa",
            reply = "Alarmnya sudah kupasang.",
            isListening = true,
            showTranscript = true,
        )

        assertEquals("berapa", caption.text)
    }

    @Test
    fun `listening with nothing heard yet clears the line rather than keeping the old answer`() {
        val caption = captionFor(null, "", "Alarmnya sudah kupasang.", isListening = true, showTranscript = true)

        assertEquals("", caption.text)
    }

    @Test
    fun `turning the text off leaves the screen wordless`() {
        assertEquals("", captionFor(null, "halo", "Halo juga", isListening = true, false).text)
        assertEquals("", captionFor(null, "", "Halo juga", isListening = false, false).text)
    }

    @Test
    fun `errors are shown even with the text turned off`() {
        // "Microphone permission is required" hidden as a preference would leave
        // a screen that simply does nothing.
        val caption = captionFor(
            errorMessage = "Microphone permission is required for hands-free mode",
            transcript = "",
            reply = "",
            isListening = false,
            showTranscript = false,
        )

        assertEquals("Microphone permission is required for hands-free mode", caption.text)
        assertFalse(caption.typed)
    }

    @Test
    fun `a blank answer is not typed out`() {
        assertFalse(captionFor(null, "", "", isListening = false, showTranscript = true).typed)
    }
}
