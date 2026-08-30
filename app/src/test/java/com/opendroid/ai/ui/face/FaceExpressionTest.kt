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
    fun `error is the only expression using the red accent`() {
        val red = FaceExpression.entries.filter { it.params().accent == FaceAccent.RED }
        assertEquals(listOf(FaceExpression.SAD), red)
    }

    @Test
    fun `sad face frowns and neutral face does not`() {
        assertTrue(FaceExpression.SAD.params().mouthCurve < 0f)
        assertTrue(FaceExpression.NEUTRAL.params().mouthCurve >= 0f)
    }

    @Test
    fun `listening widens the eyes and focusing narrows them`() {
        val neutral = FaceExpression.NEUTRAL.params().eyeOpen
        assertTrue(FaceExpression.LISTENING.params().eyeOpen > neutral)
        assertTrue(FaceExpression.FOCUSED.params().eyeOpen < neutral)
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
            assertTrue("$expression browRaise", p.browRaise in 0f..1f)
            assertTrue("$expression browAngle", p.browAngle in -45f..45f)
            assertTrue("$expression mouthOpen", p.mouthOpen in 0f..1f)
            assertTrue("$expression mouthCurve", p.mouthCurve in -1f..1f)
            assertTrue("$expression headTilt", p.headTilt in -30f..30f)
            assertTrue("$expression pupilOffsetX", p.pupilOffsetX in -1f..1f)
            assertTrue("$expression pupilOffsetY", p.pupilOffsetY in -1f..1f)
        }
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
