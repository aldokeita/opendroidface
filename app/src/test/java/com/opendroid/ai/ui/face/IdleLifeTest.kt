package com.opendroid.ai.ui.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class IdleLifeTest {

    /**
     * Beats drawn at a FIXED idle age, so a test can ask "what does the face do
     * ten seconds in" separately from "what does it do two minutes in". Letting
     * the age accumulate would make every long sample drift into the drowsy
     * branch and quietly test something else.
     */
    private fun beats(count: Int, idleFor: Long = 0L, seed: Int = 7): List<IdleBeat> {
        val random = Random(seed)
        var previous = IdleBeat.RESTING
        return List(count) {
            val beat = nextIdleBeat(random, idleFor, previous)
            previous = beat
            beat
        }
    }

    @Test
    fun `a beat never repeats itself back to back`() {
        // The same glance twice reads as a stutter, not as a look.
        val all = beats(300)
        all.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
    }

    @Test
    fun `idle beats stay inside the ranges the renderer assumes`() {
        beats(400).forEach { beat ->
            assertTrue("gazeX=${beat.gazeX}", beat.gazeX in -1f..1f)
            assertTrue("gazeY=${beat.gazeY}", beat.gazeY in -1f..1f)
            assertTrue("tilt=${beat.tilt}", beat.tilt in -10f..10f)
            assertTrue("hold=${beat.holdMillis}", beat.holdMillis in 500L..8_000L)
        }
    }

    @Test
    fun `idle life never wears a face that belongs to the agent`() {
        // Thinking, executing, listening, waiting and error are states the user
        // needs to trust. Idle fidgeting must never fake one of them.
        val forbidden = setOf(
            FaceExpression.THINKING,
            FaceExpression.LISTENING,
            FaceExpression.FOCUSED,
            FaceExpression.SPEAKING,
            FaceExpression.SAD,
        )
        beats(500, idleFor = 0L).forEach { assertTrue("$it", it.expression !in forbidden) }
        beats(500, idleFor = IDLE_DROWSY_AFTER_MILLIS).forEach {
            assertTrue("$it", it.expression !in forbidden)
        }
    }

    @Test
    fun `most beats are just a glance, not a new expression`() {
        // A face that pulled a new expression every few seconds reads as twitchy.
        val expressive = beats(500).count { it.expression != null }
        assertTrue("$expressive/500 wore an expression", expressive < 120)
    }

    @Test
    fun `sleepiness is reached only by waiting`() {
        val early = beats(400, idleFor = 0L)
        assertEquals(0, early.count { it.expression == FaceExpression.SLEEPY })

        val late = beats(400, idleFor = IDLE_DROWSY_AFTER_MILLIS)
        assertTrue("should get drowsy eventually", late.any { it.expression == FaceExpression.SLEEPY })
    }

    @Test
    fun `the face keeps moving rather than settling`() {
        // If a run of beats were all identical the face would look frozen again,
        // which is the thing this exists to prevent.
        val distinct = beats(60).distinct().size
        assertTrue("only $distinct distinct beats in 60", distinct > 8)
    }
}
