// Whether hands-free shows the words as well as speaking them.
//
// The mode is a conversation held out loud, so the text is not needed to
// understand it. But a phone is used in places where the sound is the part that
// cannot be trusted - a noisy room, a name that could be spelled two ways, a
// glance to check the assistant heard the right thing - and there the words
// help. Neither answer is right for everyone, so it is a setting.
//
// Off is not the default: silently dropping text people are used to reading is
// a worse first impression than a caption they can turn off.

package com.opendroid.ai.ui.face

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

fun transcriptLabel(visible: Boolean): String = if (visible) "Text on" else "Text off"

@Singleton
class TranscriptVisibilityStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _visible = MutableStateFlow(prefs.getBoolean(KEY_VISIBLE, true))

    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun set(value: Boolean) {
        if (_visible.value == value) return
        _visible.value = value
        prefs.edit { putBoolean(KEY_VISIBLE, value) }
    }

    fun toggle() = set(!_visible.value)

    private companion object {
        const val PREFS = "opendroid_face_transcript"
        const val KEY_VISIBLE = "visible"
    }
}
