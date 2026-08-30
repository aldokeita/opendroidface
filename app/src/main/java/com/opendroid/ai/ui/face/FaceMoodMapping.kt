// How a feeling becomes a face.
//
// The rule from PLAN.md, and it is the whole design: AgentState decides the base
// shape, emotion only modulates it. A happy face while the agent is reporting an
// error reads as broken, so emotion is allowed to speak only in the two states
// where the agent is not doing anything the face has to report — resting, and
// delivering an answer.

package com.opendroid.ai.ui.face

import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.core.face.FaceEmotion

fun FaceEmotion.expression(): FaceExpression = when (this) {
    FaceEmotion.NEUTRAL -> FaceExpression.NEUTRAL
    FaceEmotion.HAPPY -> FaceExpression.HAPPY
    FaceEmotion.GLEE -> FaceExpression.GLEE
    FaceEmotion.LOVE -> FaceExpression.LOVE
    FaceEmotion.CURIOUS -> FaceExpression.CURIOUS
    FaceEmotion.CONFUSED -> FaceExpression.CONFUSED
    FaceEmotion.WORRIED -> FaceExpression.WORRIED
    FaceEmotion.APOLOGETIC -> FaceExpression.SAD
    FaceEmotion.SURPRISED -> FaceExpression.SURPRISED
    FaceEmotion.UNIMPRESSED -> FaceExpression.UNIMPRESSED
    FaceEmotion.ANNOYED -> FaceExpression.ANNOYED
}

/**
 * The expression to draw, given what the agent is doing and how it feels.
 *
 * Thinking, executing, listening and waiting for approval keep their own faces:
 * those states are information the user needs, and a mood must not overwrite
 * them. An error keeps its face for the same reason — that is the one state
 * where the face is the error message.
 */
fun faceExpressionFor(state: AgentState, emotion: FaceEmotion?): FaceExpression {
    val base = state.toExpression()
    if (emotion == null) return base
    return when (state) {
        is AgentState.Idle, is AgentState.Speaking -> emotion.expression()
        else -> base
    }
}

/**
 * Text equivalent of a mood, for TalkBack.
 *
 * Needed because one expression can mean two things: SAD is both "the request
 * failed" and "the assistant is sorry", and a screen reader saying "something
 * went wrong" after a polite refusal would be reporting an error that did not
 * happen.
 */
fun FaceEmotion.contentDescription(): String = when (this) {
    FaceEmotion.NEUTRAL -> "Assistant face: calm"
    FaceEmotion.HAPPY -> "Assistant face: pleased"
    FaceEmotion.GLEE -> "Assistant face: delighted"
    FaceEmotion.LOVE -> "Assistant face: fond"
    FaceEmotion.CURIOUS -> "Assistant face: curious"
    FaceEmotion.CONFUSED -> "Assistant face: unsure"
    FaceEmotion.WORRIED -> "Assistant face: worried"
    FaceEmotion.APOLOGETIC -> "Assistant face: sorry"
    FaceEmotion.SURPRISED -> "Assistant face: surprised"
    FaceEmotion.UNIMPRESSED -> "Assistant face: unimpressed"
    FaceEmotion.ANNOYED -> "Assistant face: annoyed"
}

/**
 * A guess at the feeling behind a reply, from the reply itself.
 *
 * Used only when the model declared nothing. Most answers here arrive as plain
 * prose — the JSON `emotion` field exists on the planning path only — so without
 * this the face would stay blank through every conversation the assistant has.
 *
 * It is a heuristic and reads like one: a handful of markers in Indonesian and
 * English. It is deliberately biased towards NOTHING. Guessing wrong puts a
 * feeling on the assistant's face that it does not have, which is worse than a
 * calm face, so anything ambiguous returns null.
 */
fun inferEmotionFromReply(text: String): FaceEmotion? {
    val t = text.lowercase()

    // Apology first: "maaf, gagal" is an apology, not a failure report, and the
    // apology is the part the face should carry.
    if (containsAny(t, "maaf", "mohon maaf", "sorry", "apolog", "sayangnya", "unfortunately")) {
        return FaceEmotion.APOLOGETIC
    }
    if (containsAny(t, "tidak bisa", "tidak dapat", "gagal", "belum bisa", "can't", "cannot", "unable to", "failed")) {
        return FaceEmotion.WORRIED
    }
    // A question back to the user is the assistant asking, not answering.
    if (t.trimEnd().endsWith("?")) {
        return FaceEmotion.CURIOUS
    }
    if (containsAny(t, "kurang jelas", "kurang paham", "tidak yakin", "not sure", "unclear", "did you mean")) {
        return FaceEmotion.CONFUSED
    }
    if (containsAny(t, "berhasil", "selesai", "sudah saya", "sudah dibuka", "done!", "all set", "success")) {
        return FaceEmotion.GLEE
    }
    if (containsAny(t, "senang", "dengan senang hati", "tentu!", "siap!", "happy to", "glad to", "sure!")) {
        return FaceEmotion.HAPPY
    }
    return null
}

private fun containsAny(haystack: String, vararg needles: String): Boolean =
    needles.any { haystack.contains(it) }
