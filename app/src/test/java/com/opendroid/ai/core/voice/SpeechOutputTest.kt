package com.opendroid.ai.core.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechOutputTest {

    @Test
    fun `a typed question gets a silent answer`() {
        // The app talking out loud in a room where the user chose to type is the
        // behaviour this rule exists to stop.
        assertFalse(shouldSpeakReply(askedByVoice = false, speakTypedReplies = false))
    }

    @Test
    fun `a spoken question is always answered out loud`() {
        // Hands-free has no other output; a silent answer there is no answer.
        assertTrue(shouldSpeakReply(askedByVoice = true, speakTypedReplies = false))
        assertTrue(shouldSpeakReply(askedByVoice = true, speakTypedReplies = true))
    }

    @Test
    fun `the setting only ever adds speech, never removes it`() {
        assertTrue(shouldSpeakReply(askedByVoice = false, speakTypedReplies = true))
    }
}
