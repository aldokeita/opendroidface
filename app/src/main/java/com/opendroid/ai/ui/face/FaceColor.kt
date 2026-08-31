// The face's own colour, chosen by the user.
//
// The face reads as a character rather than a status light, so it keeps one
// colour across states and expresses what it is doing through shape instead.
// The single exception is an error: a red face is the one moment where colour
// carries meaning the shape alone cannot.

package com.opendroid.ai.ui.face

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class FaceColorOption(
    val id: String,
    val label: String,
    /** The bright centre of the eye; every other tone is derived from it. */
    val color: Color,
)

/**
 * Deliberately a short list of colours that stay legible on a dark panel. Very
 * dark or desaturated choices would leave the eyes unreadable, which is the one
 * thing the face cannot afford.
 */
val FACE_COLORS: List<FaceColorOption> = listOf(
    FaceColorOption("blue", "Blue", Color(0xFF4FA8FF)),
    FaceColorOption("cyan", "Cyan", Color(0xFF35E6E0)),
    FaceColorOption("green", "Green", Color(0xFF3DDC84)),
    FaceColorOption("violet", "Violet", Color(0xFF9B7BFF)),
    FaceColorOption("amber", "Amber", Color(0xFFFFB020)),
    FaceColorOption("rose", "Rose", Color(0xFFFF7597)),
)

const val DEFAULT_FACE_COLOR_ID = "blue"

fun faceColorFor(id: String?): FaceColorOption =
    FACE_COLORS.firstOrNull { it.id == id }
        ?: FACE_COLORS.first { it.id == DEFAULT_FACE_COLOR_ID }

fun nextFaceColor(id: String?): String {
    val index = FACE_COLORS.indexOfFirst { it.id == id }
    // An unknown id (a preference from an older build) restarts at the head.
    return FACE_COLORS[if (index == -1) 0 else (index + 1) % FACE_COLORS.size].id
}

@Singleton
class FaceColorStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _colorId = MutableStateFlow(
        prefs.getString(KEY_COLOR, DEFAULT_FACE_COLOR_ID) ?: DEFAULT_FACE_COLOR_ID
    )

    val colorId: StateFlow<String> = _colorId.asStateFlow()

    fun select(id: String) {
        if (_colorId.value == id) return
        _colorId.value = id
        prefs.edit { putString(KEY_COLOR, id) }
    }

    private companion object {
        const val PREFS = "opendroid_face_appearance"
        const val KEY_COLOR = "face_color"
    }
}
