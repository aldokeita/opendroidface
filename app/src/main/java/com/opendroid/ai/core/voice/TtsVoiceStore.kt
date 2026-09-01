// Which of the engine's voices to speak with.
//
// A locale is not a voice. Setting the language to Indonesian gets Indonesian
// pronunciation and whatever voice the engine happens to prefer - on this
// device, seventeen are installed and one of them is picked for you.
//
// Android's Voice carries a locale, a quality and a latency, but no gender and
// no name a person would recognise. So this stores the engine's own opaque id
// and the choosing is done by ear, in Settings, with a preview.

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
class TtsVoiceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _indonesianVoice = MutableStateFlow(prefs.getString(KEY_INDONESIAN, null))

    /** The engine voice id to use for Indonesian, or null for the engine's own choice. */
    val indonesianVoice: StateFlow<String?> = _indonesianVoice.asStateFlow()

    fun selectIndonesian(name: String?) {
        if (_indonesianVoice.value == name) return
        _indonesianVoice.value = name
        prefs.edit {
            if (name == null) remove(KEY_INDONESIAN) else putString(KEY_INDONESIAN, name)
        }
    }

    private companion object {
        const val PREFS = "opendroid_tts_voice"
        const val KEY_INDONESIAN = "indonesian_voice"
    }
}

/**
 * A short, stable label for an engine voice id.
 *
 * The ids are the engine's own (`id-id-x-idd-local`), and nothing in them says
 * who the voice sounds like. Numbering them in a fixed order at least gives the
 * user something to remember a choice by; the id stays visible underneath so a
 * preference can be recognised across devices.
 */
fun voiceDisplayLabel(name: String, index: Int): String = when {
    name.endsWith("-network") -> "Voice ${index + 1} (online)"
    else -> "Voice ${index + 1}"
}

/**
 * Offline voices first, then a stable alphabetical order.
 *
 * A network voice sounds better and needs a connection; putting them second
 * means the list starts with the ones that always work.
 */
fun sortVoicesForPicker(names: Collection<String>): List<String> =
    names.sortedWith(compareBy({ it.endsWith("-network") }, { it }))
