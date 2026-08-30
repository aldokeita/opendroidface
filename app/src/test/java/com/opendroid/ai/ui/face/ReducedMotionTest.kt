package com.opendroid.ai.ui.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionTest {

    @Test
    fun `following the device stops the face when the device stops animating`() {
        assertFalse(animationsEnabled(MotionSetting.SYSTEM, systemAnimatorScale = 0f))
        assertTrue(animationsEnabled(MotionSetting.SYSTEM, systemAnimatorScale = 1f))
    }

    @Test
    fun `a slowed-down device is not a stopped one`() {
        // Someone running animations at half speed asked for slower, not for none.
        assertTrue(animationsEnabled(MotionSetting.SYSTEM, systemAnimatorScale = 0.5f))
        assertTrue(animationsEnabled(MotionSetting.SYSTEM, systemAnimatorScale = 10f))
    }

    @Test
    fun `an unreadable device setting leaves the face alive`() {
        // Settings.Global can hand back nonsense on a modified ROM. Freezing the
        // face on the strength of that would be a worse failure than ignoring it.
        assertTrue(animationsEnabled(MotionSetting.SYSTEM, systemAnimatorScale = -1f))
    }

    @Test
    fun `the in-app choice overrides the device in both directions`() {
        assertTrue(animationsEnabled(MotionSetting.FULL, systemAnimatorScale = 0f))
        assertFalse(animationsEnabled(MotionSetting.REDUCED, systemAnimatorScale = 1f))
    }

    @Test
    fun `cycling the setting visits every option and comes back`() {
        var setting = MotionSetting.SYSTEM
        val seen = mutableListOf(setting)
        repeat(MotionSetting.entries.size) {
            setting = nextMotionSetting(setting)
            seen += setting
        }
        assertEquals(MotionSetting.entries.toSet(), seen.toSet())
        assertEquals(MotionSetting.SYSTEM, setting)
    }

    @Test
    fun `every setting has a label short enough for the control bar`() {
        MotionSetting.entries.forEach { setting ->
            val label = motionLabel(setting)
            assertTrue(label, label.isNotBlank())
            // The chip sits next to the language one at 11sp with wide letter
            // spacing; anything longer starts pushing the row apart.
            assertTrue("$label is ${label.length} chars", label.length <= 12)
        }
    }

    @Test
    fun `the labels are distinct`() {
        val labels = MotionSetting.entries.map { motionLabel(it) }
        assertEquals(labels.size, labels.toSet().size)
    }
}
