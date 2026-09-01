package com.opendroid.ai.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceStoreTest {

    private val installed = listOf(
        "id-id-x-idd-network",
        "id-id-x-dfz-local",
        "id-ID-language",
        "id-id-x-idc-network",
        "id-id-x-idc-local",
    )

    @Test
    fun `offline voices are offered before the ones that need a connection`() {
        val sorted = sortVoicesForPicker(installed)

        val firstNetwork = sorted.indexOfFirst { it.endsWith("-network") }
        val lastLocal = sorted.indexOfLast { !it.endsWith("-network") }
        assertTrue("$sorted", lastLocal < firstNetwork)
    }

    @Test
    fun `the order is stable, so a remembered choice keeps its number`() {
        assertEquals(sortVoicesForPicker(installed), sortVoicesForPicker(installed.reversed()))
    }

    @Test
    fun `a voice that needs the network says so`() {
        assertEquals("Voice 1", voiceDisplayLabel("id-id-x-dfz-local", 0))
        assertEquals("Voice 4 (online)", voiceDisplayLabel("id-id-x-idc-network", 3))
    }
}
