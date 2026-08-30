package com.opendroid.ai.ui.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceColorTest {

    @Test
    fun `the default colour exists in the palette`() {
        assertNotEquals(null, FACE_COLORS.firstOrNull { it.id == DEFAULT_FACE_COLOR_ID })
    }

    @Test
    fun `an unknown id falls back to the default rather than crashing`() {
        // A preference written by an older build must not take the face down.
        assertEquals(DEFAULT_FACE_COLOR_ID, faceColorFor("chartreuse").id)
        assertEquals(DEFAULT_FACE_COLOR_ID, faceColorFor(null).id)
    }

    @Test
    fun `cycling walks the whole palette and wraps`() {
        var id = FACE_COLORS.first().id
        val seen = mutableListOf(id)
        repeat(FACE_COLORS.size - 1) {
            id = nextFaceColor(id)
            seen.add(id)
        }
        assertEquals(FACE_COLORS.map { it.id }, seen)
        assertEquals(FACE_COLORS.first().id, nextFaceColor(id))
    }

    @Test
    fun `ids and labels are unique`() {
        assertEquals(FACE_COLORS.size, FACE_COLORS.map { it.id }.toSet().size)
        assertEquals(FACE_COLORS.size, FACE_COLORS.map { it.label }.toSet().size)
    }

    @Test
    fun `every colour is bright enough to read on a dark panel`() {
        // The eyes are the entire expression; a dim colour would make the face
        // unreadable in exactly the situation it matters, a dark room.
        FACE_COLORS.forEach { option ->
            val c = option.color
            val luminance = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
            assertTrue("${option.id} luminance=$luminance", luminance > 0.35f)
        }
    }
}
