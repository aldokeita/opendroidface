// Which face the user wants to look at.
//
// Both styles share the same expression vocabulary — the same eyes, mouths and
// icons from FaceExpression — and differ only in what surrounds them. So a new
// expression is written once and appears in both.

package com.opendroid.ai.ui.face

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class FaceStyle(val label: String, val galleryTitle: String) {
    /** Glossy eyes, a mouth and a corner icon, on a flat dark panel. */
    SCREEN("Screen", "Gallery 1 · Screen"),

    /**
     * Cozmo-style eyes on the same bare panel: two rectangles, no mouth, no
     * icons, everything said by height, width, spacing and eyelid slant.
     *
     * The enum name stays DROID because it is what earlier builds wrote into the
     * preference file.
     */
    DROID("Eyes", "Gallery 2 · Eyes"),
}

const val DEFAULT_FACE_STYLE = "SCREEN"

fun faceStyleFor(name: String?): FaceStyle =
    FaceStyle.entries.firstOrNull { it.name == name } ?: FaceStyle.SCREEN

@Singleton
class FaceStyleStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _style = MutableStateFlow(faceStyleFor(prefs.getString(KEY_STYLE, DEFAULT_FACE_STYLE)))

    val style: StateFlow<FaceStyle> = _style.asStateFlow()

    fun select(style: FaceStyle) {
        if (_style.value == style) return
        _style.value = style
        prefs.edit().putString(KEY_STYLE, style.name).apply()
    }

    private companion object {
        const val PREFS = "opendroid_face_appearance"
        const val KEY_STYLE = "face_style"
    }
}
