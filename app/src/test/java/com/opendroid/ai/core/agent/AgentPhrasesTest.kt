package com.opendroid.ai.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These are the lines the loop writes itself, so they are the ones that can
 * come back in the wrong language while the model's own answer is fine.
 */
class AgentPhrasesTest {

    private val actions = listOf(
        "OPEN_APP", "SET_ALARM", "MAKE_CALL", "TOGGLE_WIFI", "TAKE_SCREENSHOT",
        "PLAY_MUSIC", "ADD_NOTE", "SOME_ACTION_WITH_NO_ENTRY",
    )

    @Test
    fun `every action has a line in both languages, and they differ`() {
        actions.forEach { action ->
            val id = AgentPhrases.preSpeech(action, indonesian = true)
            val en = AgentPhrases.preSpeech(action, indonesian = false)
            assertTrue(action, id.isNotBlank())
            assertTrue(action, en.isNotBlank())
            assertNotEquals(action, id, en)
        }
    }

    @Test
    fun `opening an app is answered in the language it was asked in`() {
        assertEquals("Kubukakan, ya.", AgentPhrases.preSpeech("OPEN_APP", indonesian = true))
        assertEquals("Opening that for you.", AgentPhrases.preSpeech("OPEN_APP", indonesian = false))
    }

    @Test
    fun `an Indonesian goal is classified by its Indonesian words`() {
        // "buka whatsapp" is a message-and-open goal as much as "open whatsapp"
        // is; matching only English filed it under the generic "all done".
        assertEquals(
            AgentPhrases.goalDone("open whatsapp", indonesian = false),
            AgentPhrases.goalDone("open whatsapp", indonesian = false)
        )
        val indonesianGoal = AgentPhrases.goalDone("buka whatsapp", indonesian = true)
        assertNotEquals("Sudah selesai.", indonesianGoal)

        assertEquals(
            "Beres, alarmnya sudah siap.",
            AgentPhrases.goalDone("pasang alarm jam 6", indonesian = true)
        )
        assertEquals(
            "Senternya tidak mau menyala. Coba lagi?",
            AgentPhrases.failure("nyalakan senter", indonesian = true)
        )
    }

    @Test
    fun `the approval prompt never repeats the request`() {
        // Reading the user's own command back before asking for a yes spends
        // the seconds they need to answer.
        val goal = "buka whatsapp dan kirim pesan ke Budi"
        listOf(true, false).forEach { indonesian ->
            val prompt = AgentPhrases.approvalPrompt(indonesian)
            assertFalse(prompt, prompt.contains("whatsapp", ignoreCase = true))
            assertFalse(prompt, prompt.contains(goal))
            assertTrue(prompt, prompt.length < 40)
        }
    }

    @Test
    fun `an unknown action still produces something sayable`() {
        val line = AgentPhrases.preSpeech("SOME_NEW_ACTION", indonesian = true)

        assertTrue(line, line.contains("some new action"))
    }
}
