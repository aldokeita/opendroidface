package com.opendroid.ai.ui.face

import com.opendroid.ai.data.models.Plan
import com.opendroid.ai.core.agent.AgentState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class KioskModeTest {

    private fun plan() = Plan(
        planId = "p1",
        goal = "open settings",
        estimatedDuration = "5s",
        estimatedSteps = 1,
        steps = emptyList(),
    )

    @Test
    fun `the dock opens the microphone by itself when idle`() {
        assertTrue(shouldReopenMic(kiosk = true, AgentState.Idle, isListening = false, consecutiveSilences = 0))
    }

    @Test
    fun `hands-free in the hand also listens without being asked`() {
        // The whole point of the mode is not touching the phone; requiring a tap
        // between turns made a spoken conversation a sequence of taps.
        assertTrue(shouldReopenMic(kiosk = false, AgentState.Idle, isListening = false, consecutiveSilences = 0))
    }

    @Test
    fun `a stopped microphone stays stopped`() {
        // Otherwise the reopen would undo the user's tap a moment after it landed.
        assertFalse(
            shouldReopenMic(
                kiosk = false,
                state = AgentState.Idle,
                isListening = false,
                consecutiveSilences = 0,
                paused = true,
            )
        )
        assertFalse(shouldReopenMic(true, AgentState.Idle, false, 0, paused = true))
    }

    @Test
    fun `hands-free gives up sooner than the dock`() {
        // Someone is holding this phone: hand control back rather than retry for
        // an hour. A dock has nobody to hand it back to.
        assertTrue(shouldReopenMic(false, AgentState.Idle, false, HANDS_FREE_MAX_SILENT_RETRIES - 1))
        assertFalse(shouldReopenMic(false, AgentState.Idle, false, HANDS_FREE_MAX_SILENT_RETRIES))
        assertTrue(HANDS_FREE_MAX_SILENT_RETRIES < KIOSK_MAX_SILENT_RETRIES)
    }

    @Test
    fun `the dock never listens over the agent`() {
        // Re-arming mid-answer would record the assistant's own voice and cut off
        // work already in flight.
        val busy = listOf(
            AgentState.Thinking,
            AgentState.Speaking("hello"),
            AgentState.ExecutingPlan("tap"),
            AgentState.PlanProposed(plan()),
            AgentState.Listening,
        )
        busy.forEach { state ->
            assertFalse("$state", shouldReopenMic(true, state, isListening = false, consecutiveSilences = 0))
        }
    }

    @Test
    fun `an already open microphone is not opened again`() {
        assertFalse(shouldReopenMic(true, AgentState.Idle, isListening = true, consecutiveSilences = 0))
    }

    @Test
    fun `the dock gives up after enough silence`() {
        // A phone left on a stand with a refused microphone would otherwise re-arm
        // all night and be flat by morning.
        assertTrue(shouldReopenMic(true, AgentState.Idle, false, KIOSK_MAX_SILENT_RETRIES - 1))
        assertFalse(shouldReopenMic(true, AgentState.Idle, false, KIOSK_MAX_SILENT_RETRIES))
    }

    @Test
    fun `the microphone waits longer after the agent has spoken`() {
        // Speaking ends when the engine says the utterance is done, which is
        // before the sound has left the room. Reopening on the short delay lets
        // the microphone hear the tail of the assistant's own voice, and an
        // agent that answers itself keeps answering itself.
        assertTrue(reopenDelayAfter(kiosk = false, spokeLast = true) >= SPEECH_SETTLE_MILLIS)
        assertTrue(
            reopenDelayAfter(false, spokeLast = true) > reopenDelayAfter(false, spokeLast = false)
        )
    }

    @Test
    fun `the dock's own pacing is never shortened by the settle time`() {
        assertTrue(reopenDelayAfter(kiosk = true, spokeLast = false) == KIOSK_RETRY_DELAY_MILLIS)
        assertTrue(reopenDelayAfter(true, spokeLast = true) >= KIOSK_RETRY_DELAY_MILLIS)
    }

    @Test
    fun `burn-in drift stays small enough to keep the face on screen`() {
        // 0..20 minutes, sampled every 5 seconds.
        var t = 0L
        while (t <= 20 * 60_000L) {
            val (x, y) = kioskDrift(t)
            assertTrue("x=$x at $t", abs(x) <= KIOSK_DRIFT_FRACTION + 1e-4)
            assertTrue("y=$y at $t", abs(y) <= KIOSK_DRIFT_FRACTION + 1e-4)
            t += 5_000
        }
    }

    @Test
    fun `burn-in drift actually moves, and slowly`() {
        val start = kioskDrift(0)
        // A second of it must be far too small to notice - roughly a pixel on a
        // 1080-wide panel. Anyone who can see the face move is watching a bug.
        val soon = kioskDrift(1_000)
        assertTrue("moved ${abs(soon.first - start.first)} in 1s", abs(soon.first - start.first) < 0.002f)
        // ...but over minutes the face has to have gone somewhere, or the pixels
        // it sits on are lit exactly as long as they would be without any of this.
        val later = kioskDrift(60_000)
        assertTrue(abs(later.second - start.second) > 0.02f)
    }

    @Test
    fun `the two axes do not share a period`() {
        // Equal periods would trace one straight line back and forth, which lights
        // the same pixels as often as standing still does.
        val quarter = kioskDrift(56_750)
        assertTrue(abs(quarter.first - quarter.second) > 0.01f)
    }
}
