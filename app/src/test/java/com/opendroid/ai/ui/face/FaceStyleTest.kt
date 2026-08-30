package com.opendroid.ai.ui.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceStyleTest {

    @Test
    fun `the default style exists`() {
        assertEquals(FaceStyle.SCREEN, faceStyleFor(DEFAULT_FACE_STYLE))
    }

    @Test
    fun `an unknown stored style falls back instead of crashing`() {
        // A preference written by a build that had a style we later removed.
        assertEquals(FaceStyle.SCREEN, faceStyleFor("HOLOGRAM"))
        assertEquals(FaceStyle.SCREEN, faceStyleFor(null))
    }

    @Test
    fun `every style is labelled and titled`() {
        FaceStyle.entries.forEach {
            assertTrue(it.label.isNotBlank())
            assertTrue(it.galleryTitle.isNotBlank())
        }
        assertEquals(FaceStyle.entries.size, FaceStyle.entries.map { it.label }.toSet().size)
    }

    @Test
    fun `both styles draw the same expressions`() {
        // The styles differ in what surrounds the face, never in what it can feel;
        // otherwise a user would pick a look and silently lose expressions.
        assertEquals(12, FaceExpression.entries.size)
    }
}
