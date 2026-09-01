package com.opendroid.ai.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The executors report in English and this translates what it recognises.
 *
 * That leaves one failure mode worth a test: a message upstream rewrites stops
 * matching and quietly reverts to English. This measures how much of what they
 * actually say is covered, so the number moves visibly when it drops.
 */
class ActionStatusCoverageTest {

    /** Every plain success message the action executors can report. */
    private fun successMessages(): List<String> {
        val dir = File("src/main/java/com/opendroid/ai/actions")
        assertTrue("actions/ not found at ${dir.absolutePath}", dir.isDirectory)
        val pattern = Regex("""ActionResult\(\s*true\s*,\s*"([^"$\n]{4,})"""")
        return dir.listFiles { f -> f.extension == "kt" }
            .orEmpty()
            .flatMap { file -> pattern.findAll(file.readText()).map { it.groupValues[1] }.toList() }
            .distinct()
    }

    @Test
    fun `most of what an action reports on success is translated`() {
        val messages = successMessages()
        assertTrue("no success messages found - did the pattern break?", messages.size > 20)

        val untranslated = messages.filter { message ->
            AgentPhrases.localizeStatus(message, indonesian = true) == message
        }
        val covered = messages.size - untranslated.size

        // Not all of them: some end in a value, and the "<app> is open!" family
        // is handled by a pattern rather than by name.
        assertTrue(
            "Only $covered of ${messages.size} success messages translate. Untranslated:\n" +
                untranslated.joinToString("\n") { "  $it" },
            covered * 100 / messages.size >= 70
        )
    }

    @Test
    fun `an English conversation is never touched`() {
        successMessages().forEach { message ->
            assertEquals(message, AgentPhrases.localizeStatus(message, indonesian = false))
        }
    }

    @Test
    fun `a translated line is really Indonesian, not the English one echoed back`() {
        listOf(
            "Music paused!",
            "Photo saved!",
            "Couldn't toggle WiFi.",
            "This device doesn't have a camera.",
        ).forEach { message ->
            assertNotEquals(message, AgentPhrases.localizeStatus(message, indonesian = true))
        }
    }
}
