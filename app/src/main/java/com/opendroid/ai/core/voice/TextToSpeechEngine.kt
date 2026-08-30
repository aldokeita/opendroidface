package com.opendroid.ai.core.voice

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.speech.tts.TextToSpeech
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
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
            }
        }
    }

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
        val jsonPayload = """
            {
              "text": "$escapedText",
              "model_id": "eleven_monolingual_v1",
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
    }
}
