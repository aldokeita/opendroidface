// Where the current emotion lives, between the agent that declares it and the
// face that draws it.

package com.opendroid.ai.core.face

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FaceMood @Inject constructor() {

    private val _emotion = MutableStateFlow<FaceEmotion?>(null)

    /** The emotion to modulate the face with, or null when there is no opinion. */
    val emotion: StateFlow<FaceEmotion?> = _emotion.asStateFlow()

    @Volatile
    private var setAtMillis = 0L

    /**
     * Declares an emotion. Null is ignored rather than clearing: a model that
     * omitted the field has said nothing, and "nothing" must not wipe out the
     * mood the previous answer established.
     */
    fun publish(emotion: FaceEmotion?) {
        if (emotion == null) return
        _emotion.value = emotion
        setAtMillis = SystemClock.elapsedRealtime()
    }

    /** Called when a new request starts: the mood belonged to the previous answer. */
    fun clear() {
        _emotion.value = null
        setAtMillis = 0L
    }

    /**
     * Drops a mood that has been on the face too long.
     *
     * Without this the assistant keeps grinning minutes after the thing it was
     * pleased about, which reads as a stuck face rather than a felt one.
     */
    fun expireIfStale(maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS) {
        if (_emotion.value == null) return
        if (SystemClock.elapsedRealtime() - setAtMillis > maxAgeMillis) clear()
    }

    private companion object {
        const val DEFAULT_MAX_AGE_MILLIS = 45_000L
    }
}
