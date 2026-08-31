package com.opendroid.ai.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashTimingTest {

    @Test
    fun `reduced motion drops both fades`() {
        val timing = splashTiming(reduceMotion = true)

        assertEquals(0, timing.fadeInMillis)
        assertEquals(0, timing.fadeOutMillis)
    }

    @Test
    fun `reduced motion still holds long enough to be seen`() {
        // Without a hold the splash would appear and vanish inside a frame or
        // two, which reads as a flicker on launch rather than as a screen.
        assertTrue(splashTiming(reduceMotion = true).holdMillis >= 250)
    }

    @Test
    fun `animated splash fades in and out`() {
        val timing = splashTiming(reduceMotion = false)

        assertTrue(timing.fadeInMillis > 0)
        assertTrue(timing.fadeOutMillis > 0)
    }

    @Test
    fun `the animated splash lasts long enough to be watched`() {
        // Its three parts arrive in sequence - name, rule, tagline - and the last
        // of them does not start until 300ms in. Shorter than this and the
        // sequence is over before the eye has followed it, which is what the
        // 420ms version got wrong.
        assertTrue(splashTiming(reduceMotion = false).totalMillis >= 900)
    }

    @Test
    fun `neither path delays the app past two seconds`() {
        // The startup route is resolved off the main thread while the splash
        // runs, and the platform splash has already held the screen for the whole
        // cold start. Past this the splash is making the user wait on purpose.
        assertTrue(splashTiming(reduceMotion = false).totalMillis <= 2_000)
        assertTrue(splashTiming(reduceMotion = true).totalMillis <= 2_000)
    }
}
