package com.opendroid.ai.ui.face

import com.opendroid.ai.core.voice.VoiceAmplitude
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Everything the face draws from the microphone goes through here, so this is
 * where the raw, noisy rmsdB values from Android are made safe for the UI.
 */
class VoiceAmplitudeTest {

    private fun amplitude() = VoiceAmplitude()

    @Test
    fun `starts silent`() {
        assertEquals(0f, amplitude().level.value, 0f)
    }

    @Test
    fun `rms below the floor reads as silence`() {
        val a = amplitude()
        repeat(20) { a.publishRms(-50f) }
        assertEquals(0f, a.level.value, 0.02f)
    }

    @Test
    fun `rms above the ceiling never exceeds one`() {
        val a = amplitude()
        // Android documents -2..10 dB but OEMs overshoot it; unclamped values would
        // scale the mouth off the canvas.
        repeat(40) { a.publishRms(120f) }
        assertTrue(a.level.value <= 1f)
        assertTrue(a.level.value > 0.9f)
    }

    @Test
    fun `level rises faster than it falls`() {
        val rising = amplitude()
        rising.publishRms(10f)
        val afterOneLoudFrame = rising.level.value

        val falling = amplitude()
        repeat(40) { falling.publishRms(10f) }
        val loud = falling.level.value
        falling.publishRms(-2f)
        val afterOneQuietFrame = falling.level.value

        // Attack: one loud frame already moves most of the way up.
        assertTrue(afterOneLoudFrame > 0.5f)
        // Release: one quiet frame only nudges it down, so the mouth does not
        // flicker shut between syllables.
        assertTrue(loud - afterOneQuietFrame < afterOneLoudFrame)
    }

    @Test
    fun `syllable pulses open and close the mouth`() {
        // The local TTS path has no audio buffer, only word boundaries, so the mouth
        // is driven by these two states alternating.
        val a = amplitude()
        repeat(6) { a.publishSyllablePulse(open = true) }
        val open = a.level.value
        repeat(6) { a.publishSyllablePulse(open = false) }
        val closed = a.level.value

        assertTrue("open=$open", open > 0.5f)
        assertTrue("closed=$closed", closed < open / 2f)
    }

    @Test
    fun `reset silences immediately`() {
        val a = amplitude()
        repeat(20) { a.publishRms(10f) }
        assertTrue(a.level.value > 0.5f)
        a.reset()
        assertEquals(0f, a.level.value, 0f)
    }

    @Test
    fun `a silent waveform reads as silence and a loud one does not`() {
        val a = amplitude()
        // 128 is the zero point of Visualizer's 8-bit unsigned PCM.
        val silence = ByteArray(64) { 128.toByte() }
        repeat(20) { a.publishWaveform(silence) }
        assertEquals(0f, a.level.value, 0.02f)

        val loud = ByteArray(64) { if (it % 2 == 0) 0 else 255.toByte() }
        repeat(20) { a.publishWaveform(loud) }
        assertTrue(a.level.value > 0.5f)
    }

    @Test
    fun `an empty waveform decays toward silence rather than crashing`() {
        val a = amplitude()
        repeat(20) { a.publishRms(10f) }
        val loud = a.level.value
        // It fades on the release curve like any other quiet frame; only reset()
        // snaps straight to zero.
        repeat(20) { a.publishWaveform(ByteArray(0)) }
        assertTrue(a.level.value < loud * 0.1f)
    }

    @Test
    fun `the level never leaves the range the renderer assumes`() {
        val a = amplitude()
        listOf(-100f, -2f, 0f, 3.5f, 10f, 99f).forEach { rms ->
            repeat(5) { a.publishRms(rms) }
            assertTrue("rms=$rms produced ${a.level.value}", a.level.value in 0f..1f)
        }
    }
}
