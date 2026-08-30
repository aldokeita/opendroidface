// Which language the microphone listens in.
//
// Android recognises one language per session and takes it from the device
// language unless told otherwise. A phone set to English transcribes Indonesian
// speech as English-sounding nonsense, and changing the whole phone's language is
// a heavy price for talking to one app - hence this setting.

package com.opendroid.ai.ui.face

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** @param tag IETF language tag, or null to follow the device language. */
data class VoiceLanguage(val tag: String?, val label: String)

/**
 * Offered languages. Deliberately short: this is a chip the user taps through in
 * hands-free mode, not a settings list. The device entry stays first so the
 * default remains "whatever the phone does".
 */
val VOICE_LANGUAGES: List<VoiceLanguage> = listOf(
    VoiceLanguage(null, "Device"),
    VoiceLanguage("id-ID", "Indonesia"),
    VoiceLanguage("en-US", "English"),
)

fun voiceLanguageLabel(tag: String?): String =
    VOICE_LANGUAGES.firstOrNull { it.tag == tag }?.label
        ?: tag?.let { Locale.forLanguageTag(it).displayLanguage }
        ?: VOICE_LANGUAGES.first().label

fun nextVoiceLanguage(tag: String?): String? {
    val index = VOICE_LANGUAGES.indexOfFirst { it.tag == tag }
    // An unknown tag (an old preference, say) cycles back to the head of the list.
    val next = if (index == -1) 0 else (index + 1) % VOICE_LANGUAGES.size
    return VOICE_LANGUAGES[next].tag
}

@Singleton
class VoiceLanguageStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _tag = MutableStateFlow(prefs.getString(KEY_TAG, null))

    /** Selected language tag, or null to follow the device. */
    val tag: StateFlow<String?> = _tag.asStateFlow()

    fun select(newTag: String?) {
        if (_tag.value == newTag) return
        _tag.value = newTag
        prefs.edit().apply {
            if (newTag == null) remove(KEY_TAG) else putString(KEY_TAG, newTag)
        }.apply()
    }

    private companion object {
        const val PREFS = "opendroid_face_voice"
        const val KEY_TAG = "language_tag"
    }
}
