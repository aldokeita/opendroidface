// The emotion the assistant is expressing, independent of what it is doing.
//
// Kept in core rather than in ui/face because both sides need it: the agent
// writes it, the face reads it, and neither should have to import the other's
// world. This file has no Compose in it on purpose.

package com.opendroid.ai.core.face

/**
 * Emotions the LLM may declare, or that we infer from a reply.
 *
 * Deliberately a small set. Every value has to be legible at a glance on a face
 * with two eyes and a mouth, and a list the model has to choose from works
 * better short — a long one produces near-synonyms chosen at random.
 */
enum class FaceEmotion {
    NEUTRAL,
    HAPPY,
    /** Stronger than happy: a result the assistant is pleased about. */
    GLEE,
    /** Fond, warm. */
    LOVE,
    CURIOUS,
    CONFUSED,
    WORRIED,
    /** Sorry — a failure the assistant is owning. */
    APOLOGETIC,
    SURPRISED,
    UNIMPRESSED,
    ANNOYED;

    companion object {
        /**
         * Parses whatever the model wrote. Unknown or missing values return null
         * rather than a default, so a caller can tell "no opinion" from "neutral" —
         * small on-device models leave the field out entirely, and that is not the
         * same as declaring calm.
         */
        fun parse(raw: String?): FaceEmotion? {
            val cleaned = raw?.trim()?.trim('"')?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
            return when (cleaned) {
                "neutral", "calm", "normal" -> NEUTRAL
                "happy", "pleased", "glad" -> HAPPY
                "glee", "excited", "delighted", "proud" -> GLEE
                "love", "affectionate", "fond" -> LOVE
                "curious", "interested", "questioning" -> CURIOUS
                "confused", "unsure", "puzzled" -> CONFUSED
                "worried", "concerned", "anxious" -> WORRIED
                "apologetic", "sorry", "regretful", "sad" -> APOLOGETIC
                "surprised", "shocked", "amazed" -> SURPRISED
                "unimpressed", "bored", "flat" -> UNIMPRESSED
                "annoyed", "irritated", "frustrated" -> ANNOYED
                else -> null
            }
        }
    }
}
