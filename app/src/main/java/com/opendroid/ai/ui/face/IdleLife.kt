// What the face does when nobody is talking to it.
//
// A face that holds one expression perfectly still reads as a picture of a face.
// Real ones drift: the eyes wander, the head shifts, an eyebrow goes up at
// nothing. This produces those small moments — a "beat" at a time, each with its
// own gaze, tilt and duration — so the assistant looks like it is waiting rather
// than paused.
//
// Kept as a pure function so the pacing can be tested. Nothing here draws.

package com.opendroid.ai.ui.face

import kotlin.random.Random

/**
 * One idle moment.
 *
 * @param gazeX     -1f..1f, added to the resting gaze
 * @param gazeY     -1f..1f, negative looks up
 * @param tilt      degrees added to the resting head tilt
 * @param expression a brief expression to wear instead of neutral, or null to
 *                   stay neutral and only move the eyes
 * @param holdMillis how long to hold this before the next beat
 */
data class IdleBeat(
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val tilt: Float = 0f,
    val expression: FaceExpression? = null,
    val holdMillis: Long = 3_000L,
) {
    companion object {
        val RESTING = IdleBeat()
    }
}

/** How long the face waits before it starts looking sleepy. */
const val IDLE_DROWSY_AFTER_MILLIS = 120_000L

/**
 * Picks the next idle moment.
 *
 * Weighted so that most beats are a glance and a pause — a face that pulled a
 * new expression every few seconds would read as twitchy, not alive. Expressions
 * are rare and brief, and after a long wait the face starts drifting towards
 * sleepy, which is both honest and a hint that nothing is happening.
 *
 * [previous] is passed so a beat never repeats itself back to back; the same
 * glance twice reads as a stutter.
 */
fun nextIdleBeat(
    random: Random,
    idleForMillis: Long,
    previous: IdleBeat = IdleBeat.RESTING,
): IdleBeat {
    val drowsy = idleForMillis >= IDLE_DROWSY_AFTER_MILLIS
    repeat(6) {
        val beat = rollIdleBeat(random, drowsy)
        if (beat != previous) return beat
    }
    return IdleBeat.RESTING
}

private fun rollIdleBeat(random: Random, drowsy: Boolean): IdleBeat {
    // Sleepy is only ever reached by waiting, so it is rolled separately: a face
    // that yawned thirty seconds in would look bored of the user, not tired.
    if (drowsy && random.nextInt(100) < 35) {
        return IdleBeat(
            gazeY = 0.2f,
            expression = FaceExpression.SLEEPY,
            holdMillis = randomBetween(random, 3_500L, 6_000L),
        )
    }

    return when (random.nextInt(100)) {
        // Look somewhere. The bulk of idle life is this and nothing more.
        in 0..54 -> IdleBeat(
            gazeX = listOf(-0.75f, -0.45f, 0.45f, 0.75f).random(random),
            gazeY = listOf(-0.3f, 0f, 0f, 0.25f).random(random),
            tilt = listOf(-3f, 0f, 0f, 3f).random(random),
            holdMillis = randomBetween(random, 1_400L, 3_200L),
        )

        // Back to centre, held longer. The pauses are what keep the movement from
        // looking like a loop.
        in 55..79 -> IdleBeat(holdMillis = randomBetween(random, 2_500L, 5_000L))

        // Something caught its attention.
        in 80..88 -> IdleBeat(
            gazeX = if (random.nextBoolean()) 0.6f else -0.6f,
            gazeY = -0.35f,
            tilt = if (random.nextBoolean()) 5f else -5f,
            expression = FaceExpression.CURIOUS,
            holdMillis = randomBetween(random, 1_200L, 2_200L),
        )

        // A slow look around, eyes narrowed.
        in 89..95 -> IdleBeat(
            gazeX = 0.5f,
            expression = FaceExpression.SQUINT,
            holdMillis = randomBetween(random, 900L, 1_600L),
        )

        // Rare: a flicker of good mood at nothing in particular.
        else -> IdleBeat(
            expression = FaceExpression.HAPPY,
            holdMillis = randomBetween(random, 900L, 1_500L),
        )
    }
}

private fun randomBetween(random: Random, min: Long, max: Long): Long =
    min + random.nextLong(max - min + 1)
