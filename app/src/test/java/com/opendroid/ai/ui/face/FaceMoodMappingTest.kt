package com.opendroid.ai.ui.face

import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.core.face.FaceEmotion
import com.opendroid.ai.data.models.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceMoodMappingTest {

    private fun plan() = Plan(
        planId = "p1",
        goal = "open settings",
        estimatedDuration = "5s",
        estimatedSteps = 1,
        steps = emptyList(),
    )

    @Test
    fun `an emotion never overwrites a state the user needs to see`() {
        // This is the rule the whole feature rests on: a delighted face while the
        // agent is reporting an error reads as broken, not as cheerful.
        val busy = listOf(
            AgentState.Thinking,
            AgentState.Listening,
            AgentState.ExecutingPlan("tap"),
            AgentState.PlanProposed(plan()),
            AgentState.Error("boom"),
        )
        busy.forEach { state ->
            assertEquals(
                "$state",
                state.toExpression(),
                faceExpressionFor(state, FaceEmotion.GLEE),
            )
        }
    }

    @Test
    fun `emotion speaks while resting and while answering`() {
        assertEquals(
            FaceExpression.GLEE,
            faceExpressionFor(AgentState.Idle, FaceEmotion.GLEE),
        )
        assertEquals(
            FaceExpression.SAD,
            faceExpressionFor(AgentState.Speaking("maaf"), FaceEmotion.APOLOGETIC),
        )
    }

    @Test
    fun `no emotion leaves the state's own face alone`() {
        AgentState.Idle.let { assertEquals(it.toExpression(), faceExpressionFor(it, null)) }
        AgentState.Speaking("hi").let { assertEquals(it.toExpression(), faceExpressionFor(it, null)) }
    }

    @Test
    fun `every emotion maps to an expression that exists`() {
        FaceEmotion.entries.forEach { emotion ->
            // Both drawing tables are exhaustive `when`s, so reaching them at all is
            // the proof; a missing entry would not compile.
            emotion.expression().params()
            emotion.expression().oledEyes()
        }
    }

    @Test
    fun `an apology is read as an apology, not as a failure`() {
        // "Maaf, saya tidak bisa" contains both markers. The apology is the part
        // the face should carry, so it has to win.
        assertEquals(
            FaceEmotion.APOLOGETIC,
            inferEmotionFromReply("Maaf, saya tidak bisa membuka aplikasi itu."),
        )
        assertEquals(
            FaceEmotion.APOLOGETIC,
            inferEmotionFromReply("Sorry, I couldn't reach the server."),
        )
    }

    @Test
    fun `a failure without an apology reads as worry`() {
        assertEquals(
            FaceEmotion.WORRIED,
            inferEmotionFromReply("Aplikasi itu tidak bisa dibuka dari sini."),
        )
    }

    @Test
    fun `a question back to the user is curiosity`() {
        assertEquals(FaceEmotion.CURIOUS, inferEmotionFromReply("Mau saya buka yang mana?"))
    }

    @Test
    fun `a plain answer produces no emotion at all`() {
        // The heuristic is biased towards silence: a wrong guess puts a feeling on
        // the assistant's face that it does not have, which is worse than calm.
        assertNull(inferEmotionFromReply("Merah, biru, hijau."))
        assertNull(inferEmotionFromReply("Bandung adalah ibu kota Provinsi Jawa Barat."))
        assertNull(inferEmotionFromReply(""))
    }

    @Test
    fun `declared emotions parse loosely and unknown ones stay null`() {
        assertEquals(FaceEmotion.HAPPY, FaceEmotion.parse("Happy"))
        assertEquals(FaceEmotion.APOLOGETIC, FaceEmotion.parse(" \"sorry\" "))
        assertEquals(FaceEmotion.GLEE, FaceEmotion.parse("proud"))
        assertNull(FaceEmotion.parse("melancholic"))
        assertNull(FaceEmotion.parse(""))
        assertNull(FaceEmotion.parse(null))
    }
}
