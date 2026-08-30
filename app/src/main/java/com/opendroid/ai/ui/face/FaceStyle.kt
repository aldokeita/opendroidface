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
    /** A flat dark panel — the face is the screen. */
    SCREEN("Screen", "Gallery 1 · Screen"),

    /** A moulded robot head with a visor, shaded to read as a solid object. */
    DROID("Droid", "Gallery 2 · Droid"),
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
