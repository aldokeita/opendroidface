// Dock mode: the phone is on a stand, nobody is holding it, and the face is
// meant to be looked at rather than operated.
//
// What changes: the screen stays on, every control disappears, the microphone
// re-opens by itself so the user never has to touch anything, and the whole face
// drifts slowly across the panel so a static image is never burned into an OLED.
//
// The decisions that need testing live here as pure functions; the Compose side
// only applies them.

package com.opendroid.ai.ui.face

import com.opendroid.ai.core.agent.AgentState

/**
 * How many silent listens in a row before the dock gives up re-opening the
 * microphone.
 *
 * A dock with a broken or refused microphone would otherwise re-arm forever,
 * which on a phone left on a stand overnight is a flat battery by morning.
 */
const val KIOSK_MAX_SILENT_RETRIES = 30

/**
 * The same limit for hands-free in the hand.
 *
 * Lower, because someone is holding the phone: a microphone that keeps failing
 * should hand control back quickly rather than retry for an hour. Reaching it
 * pauses the microphone, and a tap starts it again.
 */
const val HANDS_FREE_MAX_SILENT_RETRIES = 8

/** How long to wait before listening again, so the retry is not a hot loop. */
const val KIOSK_RETRY_DELAY_MILLIS = 1_200L

/** Shorter in the hand: a pause between turns should feel like a conversation. */
const val HANDS_FREE_RETRY_DELAY_MILLIS = 450L

fun silenceLimitFor(kiosk: Boolean): Int =
    if (kiosk) KIOSK_MAX_SILENT_RETRIES else HANDS_FREE_MAX_SILENT_RETRIES

/**
 * How long to wait after the agent has finished speaking.
 *
 * Longer than an ordinary reopen, and not a matter of taste: the state leaves
 * Speaking when the engine says the utterance is done, which is before the last
 * of it has left the speaker and the room. Reopening on the shorter delay lets
 * the microphone catch the tail of the assistant's own voice, and an agent that
 * answers itself will keep answering itself.
 */
const val SPEECH_SETTLE_MILLIS = 1_100L

fun reopenDelayFor(kiosk: Boolean): Long =
    if (kiosk) KIOSK_RETRY_DELAY_MILLIS else HANDS_FREE_RETRY_DELAY_MILLIS

fun reopenDelayAfter(kiosk: Boolean, spokeLast: Boolean): Long =
    maxOf(reopenDelayFor(kiosk), if (spokeLast) SPEECH_SETTLE_MILLIS else 0L)

/**
 * Whether the microphone should open right now.
 *
 * Hands-free listens continuously, dock or no dock. It used to re-arm only
 * after the agent had answered, once, and otherwise waited for a tap - which
 * made a spoken conversation a sequence of taps, and the mode is meant to be
 * the one where you do not touch the phone.
 *
 * Only when the agent is idle: re-arming while it is thinking, executing or
 * speaking would record the assistant's own voice and interrupt work in flight.
 *
 * @param paused the user stopped the microphone, or too many attempts heard
 * nothing. Nothing reopens until they ask for it.
 */
fun shouldReopenMic(
    kiosk: Boolean,
    state: AgentState,
    isListening: Boolean,
    consecutiveSilences: Int,
    paused: Boolean = false,
): Boolean = !paused &&
    !isListening &&
    state is AgentState.Idle &&
    consecutiveSilences < silenceLimitFor(kiosk)

/**
 * A slow offset for the whole face, as a fraction of the screen.
 *
 * OLED panels retain a bright shape that never moves. The face already drifts a
 * few pixels while idle; this is the much slower, much wider journey that keeps
 * any one pixel from being lit for hours. It has to be slow enough that nobody
 * watching sees it move — minutes per cycle, not seconds.
 *
 * @param elapsedMillis time since the dock started
 * @return x and y as fractions of the screen, each within ±[KIOSK_DRIFT_FRACTION]
 */
fun kioskDrift(elapsedMillis: Long): Pair<Float, Float> {
    val x = kotlin.math.sin(elapsedMillis / X_PERIOD_MILLIS.toDouble() * 2 * Math.PI)
    // A different period on each axis, deliberately not a multiple of the other:
    // equal periods would trace a straight line back and forth and leave the
    // pixels along it lit as often as a stationary face would.
    val y = kotlin.math.cos(elapsedMillis / Y_PERIOD_MILLIS.toDouble() * 2 * Math.PI)
    return (x * KIOSK_DRIFT_FRACTION).toFloat() to (y * KIOSK_DRIFT_FRACTION).toFloat()
}

const val KIOSK_DRIFT_FRACTION = 0.06f
private const val X_PERIOD_MILLIS = 227_000L
private const val Y_PERIOD_MILLIS = 149_000L
