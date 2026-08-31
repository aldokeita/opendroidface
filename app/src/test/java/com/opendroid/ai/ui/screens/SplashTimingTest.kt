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
    fun `the animated splash stays shorter than half a second`() {
        // The platform splash covers the whole cold start before this composable
        // draws anything, so a generous animation here is a second logo screen
        // played after the first one has finished.
        assertTrue(splashTiming(reduceMotion = false).totalMillis <= 500)
    }

    @Test
    fun `neither path delays the app for as much as a second`() {
        // The startup route is resolved off the main thread while the splash
        // runs; anything past a second here is the splash making the user wait,
        // not the app still deciding where to go.
        assertTrue(splashTiming(reduceMotion = false).totalMillis < 1_000)
        assertTrue(splashTiming(reduceMotion = true).totalMillis < 1_000)
    }
}
