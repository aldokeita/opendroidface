package com.opendroid.ai.core.voice

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import com.opendroid.ai.BuildConfig
import com.opendroid.ai.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class TextToSpeechEngine(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    // Optional so existing callers keep working; only a caller that draws the
    // speaking voice needs to pass one.
    private val amplitude: VoiceAmplitude? = null,
    // Optional for the same reason: a caller that never speaks Indonesian has
    // no choice to honour.
    private val voiceStore: TtsVoiceStore? = null,
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Reads the played audio for lip-sync on the ElevenLabs path. */
    private var visualizer: Visualizer? = null

    /** When the most recent word boundary arrived, on the local TTS path. */
    @Volatile private var lastWordStartAt = 0L
    private var mouthTicker: Runnable? = null

    var onCompletionListener: (() -> Unit)? = null

    /**
     * The language to speak in, as an IETF tag, or null to decide per utterance.
     *
     * Same seam as [SpeechRecognitionEngine.languageTag] and fed from the same
     * setting: what the microphone listens in is what the mouth answers in.
     *
     * The app language wins over it when one has been chosen, so a person who
     * set the assistant to Indonesian is not answered in English because a
     * hands-free chip was left on something else.
     */
    var languageTag: String? = null

    /** Set from the app language; null while it is following the device. */
    var appLanguageTag: String? = null

    init {
        tts = TextToSpeech(context, this)
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                startMouthTicker()
            }

            // The local engine exposes no audio buffer, only where in the text it
            // currently is. Word boundaries are coarse compared to real syllables,
            // but they land on the right beats, which is what the face needs.
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                lastWordStartAt = SystemClock.uptimeMillis()
            }

            override fun onDone(utteranceId: String?) {
                stopMouthTicker()
                mainHandler.post {
                    onCompletionListener?.invoke()
                }
            }
            override fun onError(utteranceId: String?) {
                stopMouthTicker()
                mainHandler.post {
                    onCompletionListener?.invoke()
                }
            }
        })
    }

    /**
     * Turns word boundaries into a mouth that opens and closes.
     *
     * [android.speech.tts.UtteranceProgressListener.onRangeStart] only fires once per
     * word, so publishing on it alone would leave the mouth stuck open between
     * words. This ticker keeps publishing, open just after a boundary and closed
     * after it, and VoiceAmplitude's own smoothing turns that into a movement
     * rather than a flicker.
     */
    private fun startMouthTicker() {
        val target = amplitude ?: return
        stopMouthTicker()
        lastWordStartAt = SystemClock.uptimeMillis()
        val ticker = object : Runnable {
            override fun run() {
                val sinceWord = SystemClock.uptimeMillis() - lastWordStartAt
                target.publishSyllablePulse(sinceWord < MOUTH_OPEN_MS)
                mainHandler.postDelayed(this, MOUTH_TICK_MS)
            }
        }
        mouthTicker = ticker
        mainHandler.post(ticker)
    }

    private fun stopMouthTicker() {
        mouthTicker?.let { mainHandler.removeCallbacks(it) }
        mouthTicker = null
        amplitude?.reset()
    }

    /**
     * Reads the audio actually coming out of [mediaPlayer] so the mouth follows the
     * real waveform on the ElevenLabs path.
     *
     * Visualizer needs RECORD_AUDIO (already held for speech input) and is refused
     * outright by some OEM builds, so every failure here is swallowed: losing
     * lip-sync must never cost the user their answer.
     */
    private fun attachVisualizer(sessionId: Int) {
        releaseVisualizer()
        if (sessionId == 0) return
        try {
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?, waveform: ByteArray?, samplingRate: Int
                        ) {
                            waveform?.let { amplitude?.publishWaveform(it) }
                        }

                        override fun onFftDataCapture(
                            v: Visualizer?, fft: ByteArray?, samplingRate: Int
                        ) = Unit
                    },
                    // Half the maximum rate is plenty for a mouth and leaves the
                    // audio thread alone.
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    false,
                )
                enabled = true
            }
        } catch (e: Exception) {
            visualizer = null
        }
    }

    /** Must run on every path out of playback: a leaked Visualizer silences the next utterance. */
    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            // Already released or never started.
        }
        visualizer = null
        amplitude?.reset()
    }

    override fun onInit(status: Int) {
        // The language is chosen per utterance now, so a device locale the
        // engine happens not to carry no longer leaves this permanently mute.
        isInitialized = status == TextToSpeech.SUCCESS
        if (isInitialized && BuildConfig.DEBUG) {
            val indonesian = runCatching {
                tts?.voices.orEmpty()
                    .filter { it.locale.language == SpokenLanguage.INDONESIAN.language }
                    .map { it.name }
                    .sorted()
            }.getOrDefault(emptyList())
            Log.i(TAG, "Indonesian voices on this device: $indonesian")
        }
    }

    /**
     * Points the engine at the right language for [text] and reports whether it
     * got there.
     *
     * A locale the engine cannot speak falls back to the device's, because a
     * mispronounced answer is still an answer and silence is not. Callers do
     * not act on the result beyond that; it exists so the failure is visible in
     * debug builds rather than being a mystery on one phone.
     */
    private fun applyLanguage(text: String): Boolean {
        val engine = tts ?: return false
        val wanted = SpokenLanguage.localeFor(text, appLanguageTag ?: languageTag)
        val result = engine.setLanguage(wanted)
        if (result.isUsable()) {
            applyPreferredVoice(engine, wanted)
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Speaking as ${wanted.toLanguageTag()} as ${engine.voice?.name}")
            }
            return true
        }

        Log.w(TAG, "No voice data for ${wanted.toLanguageTag()}; falling back to the device language.")
        return engine.setLanguage(Locale.getDefault()).isUsable()
    }

    /**
     * Applies the user's chosen voice, when there is one and it belongs to the
     * language being spoken.
     *
     * Setting the language already replaced the voice, so this has to come
     * after it, every time. A choice made for Indonesian must not follow an
     * English answer: the engine would either refuse it or read English in an
     * Indonesian mouth.
     */
    private fun applyPreferredVoice(engine: TextToSpeech, spoken: Locale) {
        if (spoken.language != SpokenLanguage.INDONESIAN.language) return
        val wanted = voiceStore?.indonesianVoice?.value ?: return
        val voice = runCatching {
            engine.voices.orEmpty().firstOrNull { it.name == wanted && it.locale.language == spoken.language }
        }.getOrNull() ?: return
        engine.voice = voice
    }

    private fun Int.isUsable(): Boolean =
        this != TextToSpeech.LANG_MISSING_DATA && this != TextToSpeech.LANG_NOT_SUPPORTED

    fun speak(text: String) {
        scope.launch {
            val config = settingsRepository.llmConfig.first()
            val apiKey = config.elevenLabsApiKey
            val voiceId = config.elevenLabsVoiceId.ifEmpty { "21m00Tcm4TlvDq8ikWAM" } // default Rachel voice

            if (apiKey.isNotEmpty()) {
                try {
                    val success = playElevenLabsTts(text, apiKey, voiceId)
                    if (success) return@launch
                } catch (e: Exception) {
                    // Fallback to local TTS if ElevenLabs fails
                }
            }

            // Local fallback — must run on main thread
            if (isInitialized) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    applyLanguage(text)
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "opendroid_tts")
                }
            }
        }
    }

    private fun playElevenLabsTts(text: String, apiKey: String, voiceId: String): Boolean {
        val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId"
        val escapedText = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        // Multilingual, not monolingual. The monolingual model is English-only:
        // handed Indonesian, it renders the letters with English phonetics, and
        // the result is an English speaker sounding out words they do not know.
        val jsonPayload = """
            {
              "text": "$escapedText",
              "model_id": "eleven_multilingual_v2",
              "voice_settings": {
                "stability": 0.5,
                "similarity_boost": 0.75
              }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .addHeader("xi-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body
            
            val tempFile = File.createTempFile("elevenlabs_", ".mp3", context.cacheDir)
            tempFile.deleteOnExit()
            
            FileOutputStream(tempFile).use { fos ->
                body.byteStream().copyTo(fos)
            }

            releaseVisualizer()
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    releaseVisualizer()
                    mainHandler.post {
                        onCompletionListener?.invoke()
                    }
                }
                start()
                attachVisualizer(audioSessionId)
            }
            return true
        }
    }

    fun stop() {
        stopMouthTicker()
        releaseVisualizer()
        tts?.stop()
        mediaPlayer?.stop()
    }

    fun destroy() {
        stopMouthTicker()
        releaseVisualizer()
        tts?.shutdown()
        tts = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private companion object {
        /** How long after a word boundary the mouth stays open. */
        const val MOUTH_OPEN_MS = 110L
        /** Roughly 15fps: fast enough to look like speech, cheap enough to ignore. */
        const val MOUTH_TICK_MS = 65L
        const val TAG = "TextToSpeechEngine"
    }
}
