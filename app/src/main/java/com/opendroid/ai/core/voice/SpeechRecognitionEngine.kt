package com.opendroid.ai.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechRecognitionEngine(
    private val context: Context,
    // Optional so existing callers keep compiling and behave exactly as before; only
    // callers that draw something from the microphone level need to pass one.
    private val amplitude: VoiceAmplitude? = null,
) {

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Language tag used for the next session, e.g. "id-ID". Null follows the device
     * language. Recognition happens in one language at a time: a phone set to
     * English transcribes Indonesian speech as English-sounding nonsense, which is
     * why this is settable rather than always taken from the system.
     */
    var languageTag: String? = null

    // Identifies the currently active recognition session. Every startListening() call mints a
    // new token and each RecognitionListener callback closure captures the token it was created
    // with, comparing it against [activeSessionId] before delivering anything. This guards
    // against a stale onResults/onPartialResults/onError callback arriving from a session that
    // was already cancelled (isCancelled-style guard) *and* from a session that was superseded by
    // a newer startListening() call - either case bumps [activeSessionId] so the old callback's
    // captured token no longer matches and the delivery is dropped.
    private var activeSessionId = 0

    init {
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    /**
     * Built per session rather than once, so a language change takes effect on the
     * next utterance instead of after an app restart.
     */
    private fun buildRecognizerIntent(): Intent {
        // EXTRA_LANGUAGE is documented as an IETF tag string ("id-ID"). Passing a
        // Locale object here - as this did - is silently ignored by the recognizer,
        // which then falls back to the device language.
        val tag = languageTag?.takeIf { it.isNotBlank() } ?: Locale.getDefault().toLanguageTag()
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Prolong listening limits to avoid early cut-offs
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
    }

    fun startListening(
        onResult: (String) -> Unit,
        onPartialResult: (String) -> Unit = {},
        onError: (String) -> Unit
    ) {
        if (speechRecognizer == null) {
            onError("Speech recognition not available on this device")
            return
        }

        // Mint a new session token and let this specific listener closure capture it. Any
        // callback delivered to a listener whose captured token no longer matches
        // [activeSessionId] belongs to a cancelled or superseded session and is dropped.
        activeSessionId += 1
        val sessionId = activeSessionId

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                if (sessionId != activeSessionId) return
                amplitude?.publishRms(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // The microphone is closed from here on; leaving the last level
                // published would freeze the face mid-syllable.
                amplitude?.reset()
            }

            override fun onError(error: Int) {
                if (sessionId != activeSessionId) return
                amplitude?.reset()
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition engine busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timeout"
                    else -> "Unknown error ($error)"
                }
                onError(message)
            }

            override fun onResults(results: Bundle?) {
                if (sessionId != activeSessionId) return
                amplitude?.reset()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult(matches[0])
                } else {
                    onError("No transcription results found")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (sessionId != activeSessionId) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onPartialResult(matches[0])
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(buildRecognizerIntent())
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    /**
     * Cancels the in-progress recognition session outright. Unlike [stopListening], the
     * recognizer is guaranteed not to deliver a subsequent onResults/onPartialResults callback
     * for this session - any such callback still in flight is dropped because the session token
     * it captured no longer matches [activeSessionId] once it has been bumped here.
     */
    fun cancel() {
        activeSessionId += 1
        amplitude?.reset()
        speechRecognizer?.cancel()
    }

    fun destroy() {
        // Invalidate the active session so a callback that was already in flight cannot fire
        // (e.g. deliver a result) after this engine - and the Composable that owns it - has
        // been disposed.
        activeSessionId += 1
        amplitude?.reset()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
