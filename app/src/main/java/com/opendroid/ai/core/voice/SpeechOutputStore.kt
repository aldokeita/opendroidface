// Whether a typed question gets a spoken answer.
//
// The agent has one voice and two ways in. Answering a spoken question out loud
// is the whole point of hands-free; answering a typed one out loud is the app
// talking in a room where the user chose to be quiet - a library, a meeting, a
// sleeping house - and there is no way to ask it not to.
//
// So speech follows the question: spoken in, spoken out. This setting is the
// override for people who want everything read aloud, and it is off by default,
// because a phone that stays silent until asked is the safer surprise.

package com.opendroid.ai.core.voice

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechOutputStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _speakTypedReplies = MutableStateFlow(prefs.getBoolean(KEY_SPEAK_TYPED, false))

    /** Read answers aloud even when the question was typed. */
    val speakTypedReplies: StateFlow<Boolean> = _speakTypedReplies.asStateFlow()

    fun setSpeakTypedReplies(value: Boolean) {
        if (_speakTypedReplies.value == value) return
        _speakTypedReplies.value = value
        prefs.edit { putBoolean(KEY_SPEAK_TYPED, value) }
    }

    private companion object {
        const val PREFS = "opendroid_speech_output"
        const val KEY_SPEAK_TYPED = "speak_typed_replies"
    }
}

/**
 * Whether this answer should be spoken.
 *
 * @param askedByVoice the question that produced it arrived through the
 * microphone.
 * @param speakTypedReplies the user asked for everything to be read aloud.
 */
fun shouldSpeakReply(askedByVoice: Boolean, speakTypedReplies: Boolean): Boolean =
    askedByVoice || speakTypedReplies
