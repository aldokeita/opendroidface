// TUJUAN: app/src/main/java/com/opendroid/ai/core/voice/VoiceAmplitude.kt
//
// Sumber tunggal amplitudo suara (0f..1f) yang dipakai wajah robot.
// Diisi dari dua arah:
//   - saat MENDENGAR : SpeechRecognitionEngine.onRmsChanged  (Fase 2)
//   - saat BICARA    : Visualizer pada MediaPlayer / onRangeStart TTS lokal (Fase 3)

package com.opendroid.ai.core.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

@Singleton
class VoiceAmplitude @Inject constructor() {

    private val _level = MutableStateFlow(0f)

    /** Amplitudo ternormalisasi & sudah dihaluskan, 0f..1f. */
    val level: StateFlow<Float> = _level.asStateFlow()

    /**
     * Nilai RMS dari [android.speech.RecognitionListener.onRmsChanged].
     *
     * Android mendokumentasikan rentangnya sebagai -2f..10f dB, tapi praktiknya
     * berbeda antar-OEM dan sangat berisik — makanya di-clamp lalu di-low-pass,
     * jangan dipakai mentah ke UI.
     */
    fun publishRms(rmsdB: Float) {
        val normalized = ((rmsdB - RMS_FLOOR) / (RMS_CEILING - RMS_FLOOR)).coerceIn(0f, 1f)
        smoothTo(normalized)
    }

    /**
     * Sepotong PCM 8-bit unsigned dari [android.media.audiofx.Visualizer.getWaveForm].
     * Nilai 128 = diam, jadi simpangan dari 128 yang dihitung.
     */
    fun publishWaveform(waveform: ByteArray) {
        if (waveform.isEmpty()) {
            smoothTo(0f)
            return
        }
        var sumSquares = 0.0
        for (b in waveform) {
            val sample = (b.toInt() and 0xFF) - 128
            sumSquares += (sample * sample).toDouble()
        }
        val rms = sqrt(sumSquares / waveform.size)          // 0..128
        val normalized = (rms / WAVEFORM_FULL_SCALE).toFloat().coerceIn(0f, 1f)
        smoothTo(normalized)
    }

    /** Dipakai jalur TTS lokal yang hanya punya batas per kata, bukan audio buffer. */
    fun publishSyllablePulse(open: Boolean) {
        smoothTo(if (open) 0.7f else 0.05f)
    }

    /** Kembali ke diam. Panggil saat STT berhenti dan saat TTS selesai. */
    fun reset() {
        _level.value = 0f
    }

    /**
     * Low-pass sederhana. Naik cepat (attack) supaya responsif, turun lambat (release)
     * supaya mulut tidak berkedut di antara suku kata.
     */
    private fun smoothTo(target: Float) {
        val current = _level.value
        val alpha = if (target > current) ATTACK else RELEASE
        val next = current + (target - current) * alpha
        // Abaikan perubahan mikro supaya tidak memicu recomposition sia-sia.
        if (abs(next - current) > EPSILON || target == 0f) {
            _level.value = next
        }
    }

    private companion object {
        const val RMS_FLOOR = -2f
        const val RMS_CEILING = 10f
        const val WAVEFORM_FULL_SCALE = 64.0   // 128 = puncak teoretis; 64 terasa pas untuk suara bicara
        const val ATTACK = 0.6f
        const val RELEASE = 0.15f
        const val EPSILON = 0.01f
    }
}
