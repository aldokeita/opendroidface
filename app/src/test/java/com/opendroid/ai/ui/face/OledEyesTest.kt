package com.opendroid.ai.ui.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Style 2 has no mouth and no icons, so every expression has to be legible from
 * two rectangles alone. These tests guard the properties that legibility rests on.
 */
class OledEyesTest {

    @Test
    fun `every expression has its own eye geometry`() {
        val all = FaceExpression.entries.map { it.oledEyes() }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `no eye collapses to nothing or grows past the screen`() {
        FaceExpression.entries.forEach { expression ->
            val e = expression.oledEyes()
            assertTrue("$expression heightL", e.heightL in 0.2f..1.6f)
            assertTrue("$expression heightR", e.heightR in 0.2f..1.6f)
            assertTrue("$expression width", e.width in 0.6f..1.4f)
            assertTrue("$expression gap", e.gap in 0.6f..1.4f)
            assertTrue("$expression offsetY", e.offsetY in -1f..1f)
            assertTrue("$expression radius", e.radius in 0f..0.5f)
        }
    }

    @Test
    fun `only the hostile expressions drop their inner corners`() {
        // Slope sign is the whole grammar of this face: positive lowers the OUTER
        // corner (sad, worried), negative lowers the INNER one, which is the only
        // way it can look angry. An expression that is not meant to be hostile
        // must never go negative by accident.
        val hostile = setOf(
            FaceExpression.ANNOYED,
            FaceExpression.ANGRY,
            FaceExpression.FURIOUS,
            FaceExpression.FRUSTRATED,
            FaceExpression.SUSPICIOUS,
            FaceExpression.CONFUSED,
        )
        assertTrue(FaceExpression.SAD.oledEyes().slopeL > 0f)
        assertTrue(FaceExpression.WORRIED.oledEyes().slopeL > 0f)

        FaceExpression.entries.filterNot { it in hostile }.forEach { expression ->
            val e = expression.oledEyes()
            assertTrue("$expression slopeL", e.slopeL >= 0f)
            assertTrue("$expression slopeR", e.slopeR >= 0f)
        }
    }

    @Test
    fun `anger escalates with the slope, not with size`() {
        // annoyed -> angry -> furious has to read as a progression, and the only
        // dimension that carries it is how far the inner corner falls.
        val annoyed = FaceExpression.ANNOYED.oledEyes().slopeL
        val angry = FaceExpression.ANGRY.oledEyes().slopeL
        val furious = FaceExpression.FURIOUS.oledEyes().slopeL
        assertTrue("$annoyed > $angry", annoyed > angry)
        assertTrue("$angry > $furious", angry > furious)
    }

    @Test
    fun `focused and sleepy are flatter than neutral, surprised is taller`() {
        val neutral = FaceExpression.NEUTRAL.oledEyes().heightL
        assertTrue(FaceExpression.FOCUSED.oledEyes().heightL < neutral)
        assertTrue(FaceExpression.SLEEPY.oledEyes().heightL < FaceExpression.FOCUSED.oledEyes().heightL)
        assertTrue(FaceExpression.SURPRISED.oledEyes().heightL > neutral)
    }

    @Test
    fun `asymmetry is what carries curiosity and confusion`() {
        // With no mouth and no brows, one eye differing from the other is the only
        // signal left for "questioning".
        listOf(FaceExpression.CURIOUS, FaceExpression.CONFUSED).forEach {
            val e = it.oledEyes()
            assertTrue("$it should be asymmetric", e.heightL != e.heightR || e.slopeL != e.slopeR)
        }
    }

    @Test
    fun `smiling eyes are the flat-topped round-bottomed ones`() {
        assertTrue(FaceExpression.HAPPY.oledEyes().bottomHeavy)
        assertTrue(FaceExpression.HAPPY.oledEyes().heightL < FaceExpression.NEUTRAL.oledEyes().heightL)
    }
}
