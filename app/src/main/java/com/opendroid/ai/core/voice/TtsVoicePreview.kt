// A throwaway TTS engine for the Settings voice picker.
//
// The speaking engine belongs to the foreground service, which is where answers
// come from. Settings needs two things that service cannot easily lend out: the
// list of voices installed right now, and a way to speak one sample line so the
// user can hear which voice is which. Both are cheap, so they get their own
// short-lived engine that is shut down when the screen goes away.

package com.opendroid.ai.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsVoicePreview(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var onVoices: ((List<String>) -> Unit)? = null

    /** @param onVoices the voice ids installed for [locale], once the engine is up. */
    fun start(locale: Locale, onVoices: (List<String>) -> Unit) {
        this.onVoices = onVoices
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                this.onVoices?.invoke(emptyList())
                return@TextToSpeech
            }
            val names = runCatching {
                tts?.voices.orEmpty()
                    .filter { it.locale.language == locale.language }
                    .map { it.name }
            }.getOrDefault(emptyList())
            this.onVoices?.invoke(sortVoicesForPicker(names))
        }
    }

    /** Speaks [text] in [voiceName], interrupting any previous preview. */
    fun preview(voiceName: String, text: String) {
        val engine = tts ?: return
        val voice = runCatching { engine.voices.orEmpty().firstOrNull { it.name == voiceName } }
            .getOrNull() ?: return
        engine.voice = voice
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "opendroid_voice_preview")
    }

    fun release() {
        onVoices = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}
