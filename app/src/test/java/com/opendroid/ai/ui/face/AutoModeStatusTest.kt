package com.opendroid.ai.ui.face

import com.opendroid.ai.R
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.data.models.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * In Auto mode this label is the only text on screen, so which one is chosen is
 * part of the interface, not a detail. It returns a resource now, so the words
 * themselves live in strings.xml and exist in both languages.
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
        // The mic opens before the agent has moved off Idle; showing "tap to
        // speak" while already recording tells the user the opposite of the truth.
        assertEquals(
            R.string.hands_free_listening,
            autoModeStatusLabel(AgentState.Idle, isListening = true)
        )
        assertEquals(
            R.string.hands_free_listening,
            autoModeStatusLabel(AgentState.Thinking, isListening = true)
        )
    }

    @Test
    fun `an idle gap between turns is still listening, not an invitation to tap`() {
        // The microphone reopens on its own, so the gap after an answer is a
        // pause in a conversation, not a screen waiting to be touched.
        assertEquals(
            R.string.hands_free_listening,
            autoModeStatusLabel(AgentState.Idle, isListening = false)
        )
    }

    @Test
    fun `only a stopped microphone invites the user to tap`() {
        assertEquals(
            R.string.hands_free_tap_to_speak,
            autoModeStatusLabel(AgentState.Idle, isListening = false, paused = true)
        )
    }

    @Test
    fun `executing reports work in progress`() {
        // The step's own description is preferred by the composable; the label
        // is what covers a step that has none.
        assertEquals(
            R.string.hands_free_working,
            autoModeStatusLabel(AgentState.ExecutingPlan("Opening Settings"), isListening = false)
        )
    }

    @Test
    fun `every state resolves to a real string resource`() {
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
                assertNotEquals("$state listening=$listening", 0, autoModeStatusLabel(state, listening))
            }
        }
    }

    @Test
    fun `a raw error message is never shown as the status line`() {
        // Errors reach the user through the caption; the status line stays a
        // short human sentence so the face keeps reading as a face, not a log.
        assertEquals(
            R.string.hands_free_error,
            autoModeStatusLabel(AgentState.Error("java.net.SocketTimeoutException"), false)
        )
    }
}
