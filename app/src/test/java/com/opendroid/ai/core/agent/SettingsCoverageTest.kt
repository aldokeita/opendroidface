package com.opendroid.ai.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Someone driving their phone entirely by voice cannot reach over and tap "try
 * again", so the settings they ask for most have to land without an LLM round
 * trip, and every action the model can name has to exist.
 */
class SettingsCoverageTest {

    @Test
    fun `the everyday device commands never wait for a model`() {
        val instant = mapOf(
            "nyalakan senter" to "TOGGLE_FLASHLIGHT",
            "matikan wifi" to "TOGGLE_WIFI",
            "kunci layar" to "LOCK_SCREEN",
            "rotasi layar" to "TOGGLE_AUTO_ROTATE",
            "matikan kecerahan otomatis" to "TOGGLE_AUTO_BRIGHTNESS",
            "matikan suara sentuh" to "TOGGLE_TOUCH_SOUNDS",
            "getar dering" to "TOGGLE_VIBRATE_ON_RING",
            "tangkapan layar" to "TAKE_SCREENSHOT",
        )
        instant.forEach { (phrase, action) ->
            assertEquals(phrase, action, AliasResolver.resolve(phrase)?.action)
        }
    }

    @Test
    fun `settings the app cannot write land on their own page, not the front door`() {
        listOf(
            "mode pesawat" to "airplane",
            "pengaturan lokasi" to "location",
            "pengaturan baterai" to "battery",
            "opsi pengembang" to "developer",
        ).forEach { (phrase, page) ->
            val hint = AliasResolver.resolve(phrase)
            assertEquals(phrase, "OPEN_SETTINGS_PAGE", hint?.action)
            assertEquals(phrase, page, hint?.baseParams?.get("page"))
        }
    }

    @Test
    fun `every action the schema advertises is one the dispatcher can run`() {
        // A name in the schema the executor does not have is a plan that fails
        // after the user has already been told it was understood.
        val advertised = ActionSchema.ALL_ACTIONS.map { it.name }.toSet()
        listOf(
            "SET_SCREEN_TIMEOUT", "TOGGLE_AUTO_ROTATE", "SET_FONT_SCALE",
            "TOGGLE_HAPTIC_FEEDBACK", "TOGGLE_AUTO_BRIGHTNESS", "TOGGLE_TOUCH_SOUNDS",
            "TOGGLE_VIBRATE_ON_RING", "OPEN_SETTINGS_PAGE",
        ).forEach { name ->
            assertTrue(name, name in advertised)
        }
    }

    @Test
    fun `the settings-page action documents that it only opens a page`() {
        // It must never report a setting as changed: it cannot change one.
        val definition = ActionSchema.ALL_ACTIONS.first { it.name == "OPEN_SETTINGS_PAGE" }

        assertNotNull(definition)
        assertTrue(
            definition.description,
            definition.description.contains("NEVER claim", ignoreCase = true)
        )
    }
}

