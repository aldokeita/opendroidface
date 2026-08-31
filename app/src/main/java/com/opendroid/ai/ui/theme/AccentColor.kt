// The app's accent colour, chosen by the owner.
//
// Until now there were two: screens used the palette's neon green for every
// selected state, button and link, while the navigation bar drew itself in red.
// Two accents on one app is not a choice, it is a disagreement - so the choice
// is made once here and every screen reads it, the nav bar and its glow
// included.
//
// What this does NOT change: cyan, orange and red keep their jobs as
// information, warning and error. A palette where the accent can become red is
// a palette where "accent" and "something went wrong" would otherwise be the
// same colour, and the error red has to stay the one that means that.

package com.opendroid.ai.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AccentOption(
    val id: String,
    val label: String,
    /** On the dark palette. */
    val dark: Color,
    /** On the light palette, where the same hue has to survive a white ground. */
    val light: Color,
)

/**
 * Deliberately short. Every one of these has to stay legible as a 2dp line on a
 * near-black bar and as text on a white card, which rules out anything pale or
 * muddy - and a list long enough to scroll would make a decision out of what
 * should be a glance.
 */
val ACCENT_OPTIONS: List<AccentOption> = listOf(
    AccentOption("red", "Red", Color(0xFFFF3B30), Color(0xFFCF222E)),
    AccentOption("green", "Green", Color(0xFF00FF88), Color(0xFF1A7F37)),
    AccentOption("cyan", "Cyan", Color(0xFF00F0FF), Color(0xFF0969DA)),
    AccentOption("violet", "Violet", Color(0xFF9B7BFF), Color(0xFF7A3EE8)),
    AccentOption("amber", "Amber", Color(0xFFFFB020), Color(0xFFB45309)),
    AccentOption("rose", "Rose", Color(0xFFFF7597), Color(0xFFC2255C)),
)

/**
 * Red, to match the navigation bar rather than fight it.
 *
 * The bar was drawn in red before this setting existed and the owner kept it;
 * defaulting to green would have meant shipping a setting whose default undoes
 * the thing it was added to make consistent.
 */
const val DEFAULT_ACCENT_ID = "red"

fun accentOptionFor(id: String?): AccentOption =
    ACCENT_OPTIONS.firstOrNull { it.id == id }
        ?: ACCENT_OPTIONS.first { it.id == DEFAULT_ACCENT_ID }

@Singleton
class AccentStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _accentId = MutableStateFlow(
        prefs.getString(KEY_ACCENT, DEFAULT_ACCENT_ID) ?: DEFAULT_ACCENT_ID
    )

    val accentId: StateFlow<String> = _accentId.asStateFlow()

    fun select(id: String) {
        if (_accentId.value == id) return
        _accentId.value = id
        prefs.edit { putString(KEY_ACCENT, id) }
    }

    private companion object {
        const val PREFS = "opendroid_appearance"
        const val KEY_ACCENT = "accent"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccentEntryPoint {
    fun accentStore(): AccentStore
}

/**
 * The process-wide accent selection.
 *
 * Reached through an entry point rather than injection because the theme wraps
 * everything, including screens that are not Hilt-aware.
 */
@Composable
fun rememberAccentStore(): AccentStore {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, AccentEntryPoint::class.java).accentStore()
    }
}

/**
 * The palette with the chosen accent in place of the built-in green.
 *
 * Both green fields are replaced: `accentNeonGreen` is the thin-mark accent and
 * `accentGreenButton` the one for large filled surfaces, and a screen that used
 * one while another used the other would end up two-toned.
 */
fun OpenDroidColors.withAccent(option: AccentOption): OpenDroidColors {
    val accent = if (isDark) option.dark else option.light
    return copy(accentNeonGreen = accent, accentGreenButton = accent)
}
