package com.opendroid.ai.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The alias fast path exists to skip the planner for requests the planner would
 * only slow down. It must never skip it for a request the planner is needed for.
 */
class AliasScopeTest {

    @Test
    fun `a bare app request still takes the shortcut`() {
        assertEquals("OPEN_APP", AliasResolver.resolve("open whatsapp")?.action)
        assertEquals("OPEN_APP", AliasResolver.resolve("whatsapp")?.action)
        assertEquals("OPEN_APP", AliasResolver.resolve("tolong buka whatsapp")?.action)
        assertEquals("OPEN_APP", AliasResolver.resolve("buka aplikasi whatsapp dong")?.action)
    }

    @Test
    fun `naming something inside the app goes to the planner instead`() {
        // This is the bug: the sentence merely contained "whatsapp", so the
        // shortcut opened the app and the conversation was never reached.
        assertNull(AliasResolver.resolve("bukakan percakapan whatsapp istriku"))
        assertNull(AliasResolver.resolve("open my whatsapp conversation with Sarah"))
        assertNull(AliasResolver.resolve("buka chat whatsapp dari Budi"))
    }

    @Test
    fun `an Indonesian compound request is not swallowed either`() {
        // The compound guard was English-only, so nothing in an Indonesian
        // sentence tripped it.
        assertNull(AliasResolver.resolve("buka whatsapp dan kirim pesan ke Budi"))
        assertNull(AliasResolver.resolve("telepon istriku lewat whatsapp"))
    }

    @Test
    fun `reaching into an app is never planned as a single step`() {
        // classifyComplexity is pure; the provider is only used by the
        // suspending LLM path, which this never reaches.
        val classifier = IntentClassifier { error("not used by classifyComplexity") }

        // One step can only open the app. Three are needed: open, wait, tap.
        listOf(
            "bukakan percakapan whatsapp istriku",
            "open my conversation with Sarah",
            "baca pesan dari Budi",
        ).forEach { query ->
            assertEquals(query, QueryComplexity.MEDIUM, classifier.classifyComplexity(query))
        }
    }
}
