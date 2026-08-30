package com.opendroid.ai.ui.face

import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.data.models.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In Auto mode this label is the only text on screen, so its wording is part of
 * the interface, not a detail.
 */
class AutoModeStatusTest {

    private fun plan() = Plan(
        planId = "p1",
        goal = "open settings",
        estimatedDuration = "5s",
        estimatedSteps = 1,
        steps = emptyList(),
    )

    @Test
    fun `microphone state wins over the agent state`() {
        // The mic opens before the agent has moved off Idle; showing "Tap to speak"
        // while already recording would tell the user the opposite of the truth.
        assertEquals("Listening…", autoModeStatusLabel(AgentState.Idle, isListening = true))
        assertEquals("Listening…", autoModeStatusLabel(AgentState.Thinking, isListening = true))
    }

    @Test
    fun `idle with a closed microphone invites the user to speak`() {
        assertEquals("Tap to speak", autoModeStatusLabel(AgentState.Idle, isListening = false))
    }

    @Test
    fun `executing shows the current step and never an empty line`() {
        assertEquals(
            "Opening Settings",
            autoModeStatusLabel(AgentState.ExecutingPlan("Opening Settings"), isListening = false)
        )
        assertEquals(
            "Working on it…",
            autoModeStatusLabel(AgentState.ExecutingPlan("   "), isListening = false)
        )
    }

    @Test
    fun `every state produces a non-blank label`() {
        val states = listOf(
            AgentState.Idle,
            AgentState.Listening,
            AgentState.Thinking,
            AgentState.PlanProposed(plan()),
            AgentState.ExecutingPlan("tap"),
            AgentState.Speaking("hello"),
            AgentState.Error("boom"),
        )
        states.forEach { state ->
            listOf(true, false).forEach { listening ->
                assertTrue(
                    "$state listening=$listening",
                    autoModeStatusLabel(state, listening).isNotBlank()
                )
            }
        }
    }

    @Test
    fun `a raw error message is never shown as the status line`() {
        // Errors reach the user through the subtitle slot; the status line stays a
        // short human sentence so the face keeps reading as a face, not a log.
        val label = autoModeStatusLabel(AgentState.Error("java.net.SocketTimeoutException"), false)
        assertEquals("Something went wrong", label)
    }
}
