package com.opendroid.ai.ui.face

import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.data.models.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state -> expression mapping is the one piece of face logic with no Compose
 * in it, which is why it lives in its own file: it can be tested on the JVM
 * without a Compose test runtime.
 */
class FaceExpressionTest {

    private fun plan() = Plan(
        planId = "p1",
        goal = "open settings",
        estimatedDuration = "5s",
        estimatedSteps = 1,
        steps = emptyList(),
    )

    @Test
    fun `every agent state maps to its expression`() {
        assertEquals(FaceExpression.NEUTRAL, AgentState.Idle.toExpression())
        assertEquals(FaceExpression.LISTENING, AgentState.Listening.toExpression())
        assertEquals(FaceExpression.THINKING, AgentState.Thinking.toExpression())
        assertEquals(FaceExpression.CURIOUS, AgentState.PlanProposed(plan()).toExpression())
        assertEquals(FaceExpression.FOCUSED, AgentState.ExecutingPlan("tap").toExpression())
        assertEquals(FaceExpression.SPEAKING, AgentState.Speaking("hi").toExpression())
        assertEquals(FaceExpression.SAD, AgentState.Error("boom").toExpression())
    }

    @Test
    fun `mapping ignores the payload carried by a state`() {
        assertEquals(
            AgentState.ExecutingPlan("step one").toExpression(),
            AgentState.ExecutingPlan("step forty").toExpression(),
        )
        assertEquals(
            AgentState.Error("network").toExpression(),
            AgentState.Error("parse failure").toExpression(),
        )
    }

    @Test
    fun `a failure is signalled by shape and icon, not by colour`() {
        // The face keeps the colour the user picked in every state, so it reads as
        // one character. What went wrong is said by the downcast, half-lidded eyes,
        // the frown, and the alert icon.
        val sad = FaceExpression.SAD.params()
        assertEquals(FaceIcon.ALERT, sad.icon)
        assertTrue("eyes should be half-lidded", sad.eyeSquint > 0.3f)
        assertTrue("gaze should fall", sad.gazeY > 0f)
        assertTrue("mouth should frown", sad.mouthCurve < 0f)
    }

    @Test
    fun `sad face frowns and neutral face does not`() {
        assertTrue(FaceExpression.SAD.params().mouthCurve < 0f)
        assertTrue(FaceExpression.NEUTRAL.params().mouthCurve >= 0f)
    }

    @Test
    fun `a tilted head never rotates straight-line eyes far`() {
        // Head tilt rotates everything, including eyes drawn as straight lines. A
        // sleepy face at eight degrees reads as broken, not as sleepy.
        FaceExpression.entries
            .filter { it.params().eyeStyle == EyeStyle.LINE }
            .forEach { assertTrue("$it", kotlin.math.abs(it.params().headTilt) <= 3f) }
    }

    @Test
    fun `listening widens the eyes and focusing narrows them`() {
        // Size is carried by eyeScale; eyeOpen is the lid, which the blink drives.
        val neutral = FaceExpression.NEUTRAL.params().eyeScale
        assertTrue(FaceExpression.LISTENING.params().eyeScale > neutral)
        assertTrue(FaceExpression.FOCUSED.params().eyeScale < neutral)
        assertTrue(FaceExpression.FOCUSED.params().eyeSquint > 0f)
    }

    @Test
    fun `parameters stay inside the ranges the renderer assumes`() {
        // RobotFace derives geometry directly from these; a value outside the
        // documented range would draw a face that is inverted or off-canvas.
        FaceExpression.entries.forEach { expression ->
            val p = expression.params()
            assertTrue("$expression eyeOpen", p.eyeOpen in 0f..2f)
            assertTrue("$expression eyeSquint", p.eyeSquint in 0f..1f)
            assertTrue("$expression eyeScale", p.eyeScale in 0.5f..2f)
            assertTrue("$expression lidAngle", p.lidAngle in -45f..45f)
            assertTrue("$expression mouthOpen", p.mouthOpen in 0f..1f)
            assertTrue("$expression mouthCurve", p.mouthCurve in -1f..1f)
            assertTrue("$expression headTilt", p.headTilt in -30f..30f)
            assertTrue("$expression gazeX", p.gazeX in -1f..1f)
            assertTrue("$expression gazeY", p.gazeY in -1f..1f)
        }
    }

    @Test
    fun `thinking does not use an angry lid`() {
        // A tilted eyelid is the only way this face can look angry. Thinking used
        // one and read as irritation; it now looks up and away instead.
        val thinking = FaceExpression.THINKING.params()
        assertEquals(0f, thinking.lidAngle, 0f)
        assertTrue("gaze should leave centre", thinking.gazeX != 0f || thinking.gazeY != 0f)
        assertEquals(FaceIcon.DOTS, thinking.icon)
    }

    @Test
    fun `no expression tilts a lid without meaning to look angry`() {
        // Guards the redesign: if a future expression wants a slanted lid it has to
        // be a deliberate choice made here, not inherited by accident.
        FaceExpression.entries.forEach { expression ->
            assertEquals("$expression", 0f, expression.params().lidAngle, 0f)
        }
    }

    @Test
    fun `expressions are visually distinct from one another`() {
        // Two expressions that produce identical geometry are indistinguishable on
        // screen, which makes one of them a lie.
        val shapes = FaceExpression.entries.map { it.params() }
        assertEquals(shapes.size, shapes.toSet().size)
    }

    @Test
    fun `only shape-changed eyes skip the blink`() {
        // RobotFace blinks a ROUND eye only; an arc or a heart has no lid to close.
        // Every state reachable from AgentState keeps a blinking eye, so the face
        // never looks frozen during normal use.
        val reachable = listOf(
            FaceExpression.NEUTRAL,
            FaceExpression.LISTENING,
            FaceExpression.THINKING,
            FaceExpression.CURIOUS,
            FaceExpression.FOCUSED,
            FaceExpression.SPEAKING,
        )
        reachable.forEach { assertEquals("$it", EyeStyle.ROUND, it.params().eyeStyle) }
    }

    @Test
    fun `an open microphone makes an idle face listen`() {
        assertEquals(AgentState.Listening, faceStateFor(AgentState.Idle, micOpen = true))
        assertEquals(AgentState.Idle, faceStateFor(AgentState.Idle, micOpen = false))
    }

    @Test
    fun `an open microphone clears a stale error`() {
        // Error never clears by itself, so without this the face stays sad while
        // the user is already speaking their next request.
        assertEquals(AgentState.Listening, faceStateFor(AgentState.Error("boom"), micOpen = true))
        assertEquals(
            AgentState.Error("boom"),
            faceStateFor(AgentState.Error("boom"), micOpen = false)
        )
    }

    @Test
    fun `an open microphone never masks what the agent is doing`() {
        val busy = listOf(
            AgentState.Thinking,
            AgentState.ExecutingPlan("tap"),
            AgentState.Speaking("hello"),
            AgentState.PlanProposed(plan()),
        )
        busy.forEach { state ->
            assertEquals(state, faceStateFor(state, micOpen = true))
        }
    }

    @Test
    fun `each expression has its own content description for TalkBack`() {
        val descriptions = FaceExpression.entries.map { it.contentDescription() }
        assertEquals(descriptions.size, descriptions.toSet().size)
        descriptions.forEach { assertNotEquals("", it) }
    }
}
