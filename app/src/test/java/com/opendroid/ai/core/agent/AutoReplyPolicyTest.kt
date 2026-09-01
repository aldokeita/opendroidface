package com.opendroid.ai.core.agent

import com.opendroid.ai.data.models.AutoReplyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These decide whether a message goes out in someone else's name. A wrong
 * answer is not a bug the user finds later - it is a message another person has
 * already read.
 */
class AutoReplyPolicyTest {

    private val config = AutoReplyConfig(
        globalEnabled = true,
        whatsappEnabled = true,
        whitelistedContacts = setOf("Sayangku 🫶🏻", "Aldoki"),
        contactNotes = mapOf("Sayangku 🫶🏻" to "my wife"),
    )

    @Test
    fun `an empty allowlist means nobody, not everybody`() {
        // The dangerous reading of an empty box. Auto-reply used to answer
        // every contact the moment it was switched on.
        val empty = config.copy(whitelistedContacts = emptySet())

        assertFalse(AutoReplyPolicy.isAllowed("Sayangku 🫶🏻", empty))
        assertFalse(AutoReplyPolicy.isAllowed("anyone at all", empty))
    }

    @Test
    fun `listed contacts are allowed and everyone else is not`() {
        assertTrue(AutoReplyPolicy.isAllowed("Sayangku 🫶🏻", config))
        assertTrue(AutoReplyPolicy.isAllowed("Aldoki", config))
        assertFalse(AutoReplyPolicy.isAllowed("Budi", config))
        assertFalse(AutoReplyPolicy.isAllowed(null, config))
        assertFalse(AutoReplyPolicy.isAllowed("", config))
    }

    @Test
    fun `an emoji in the contact name is not part of who they are`() {
        // WhatsApp names carry emoji, skin tones and zero-width joiners.
        // Nobody should have to reproduce those to make their own list work.
        assertTrue(AutoReplyPolicy.isAllowed("Sayangku", config))
        assertTrue(AutoReplyPolicy.isAllowed("  sayangku 🫶🏻 ", config))
        assertTrue(AutoReplyPolicy.isAllowed("ALDOKI", config))
    }

    @Test
    fun `a blocked contact stays blocked even if also listed`() {
        val contradictory = config.copy(blacklistedContacts = setOf("Aldoki"))

        assertFalse(AutoReplyPolicy.isAllowed("Aldoki", contradictory))
        assertTrue(AutoReplyPolicy.isAllowed("Sayangku 🫶🏻", contradictory))
    }

    @Test
    fun `the note about a contact is found the same forgiving way`() {
        assertEquals("my wife", AutoReplyPolicy.noteFor("Sayangku", config))
        assertEquals("my wife", AutoReplyPolicy.noteFor("sayangku 🫶🏻", config))
        assertNull(AutoReplyPolicy.noteFor("Aldoki", config))
    }

    @Test
    fun `a model that says it cannot tell is not sent as a message`() {
        // Otherwise the contact receives the literal word "SKIP".
        assertFalse(AutoReplyPolicy.shouldSend("SKIP"))
        assertFalse(AutoReplyPolicy.shouldSend("  skip "))
        assertFalse(AutoReplyPolicy.shouldSend("\"SKIP\""))
        assertFalse(AutoReplyPolicy.shouldSend("SKIP."))
        assertFalse(AutoReplyPolicy.shouldSend(null))
        assertFalse(AutoReplyPolicy.shouldSend("   "))
    }

    @Test
    fun `a real reply that happens to mention skipping is still sent`() {
        assertTrue(AutoReplyPolicy.shouldSend("Aku skip dulu ya, nanti kususul"))
        assertTrue(AutoReplyPolicy.shouldSend("otw ya sayang 🫶🏻"))
    }
}
