package com.opendroid.ai.data.repository

import com.opendroid.ai.data.models.AutoReplyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.memberProperties

/**
 * A settings field that the UI writes and the repository quietly ignores looks
 * exactly like a working feature until the user comes back to the screen and
 * finds their typing gone. That happened; this is the guard against it
 * happening to the next field.
 */
class AutoReplyPersistenceTest {

    /**
     * Every property of [AutoReplyConfig] has to be named somewhere in
     * SettingsRepository - once to write it, once to read it back.
     */
    @Test
    fun `every field of the config is both written and read`() {
        val source = java.io.File(
            "src/main/java/com/opendroid/ai/data/repository/SettingsRepository.kt"
        )
        assertTrue("SettingsRepository.kt not found at ${source.absolutePath}", source.exists())
        val text = source.readText()

        val readBlock = text.substringAfter("val autoReplyConfig: Flow<AutoReplyConfig>")
            .substringBefore("suspend fun updateConfig")
        val writeBlock = text.substringAfter("suspend fun updateAutoReplyConfig")

        val missing = AutoReplyConfig::class.memberProperties
            .map { it.name }
            .filterNot { field -> readBlock.contains(field) && writeBlock.contains(field) }
            .sorted()

        assertEquals(
            "Config fields the repository never persists or never reads back: $missing",
            emptyList<String>(),
            missing
        )
    }

    @Test
    fun `the persona and style notes are part of the config`() {
        // Named explicitly, so deleting them from the model fails here rather
        // than silently removing a feature.
        val fields = AutoReplyConfig::class.memberProperties.map { it.name }.toSet()

        assertTrue(fields.containsAll(setOf("personaNotes", "styleNotes", "contactNotes")))
    }
}
