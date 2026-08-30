// Whether the face is allowed to move.
//
// The face blinks, drifts, glances around and pulses rings more or less
// continuously. For a user who is sensitive to motion that is not decoration,
// it is an obstacle; for a TalkBack user none of it carries information at all,
// since every expression already has a spoken equivalent, and it costs battery
// for nothing.
//
// Android already knows the answer: a user who turns animations off in
// Developer options or in Accessibility sets ANIMATOR_DURATION_SCALE to 0. That
// is the default source of truth here. The in-app setting exists because the
// system switch is buried, and because someone may want a still face without
// flattening every other app they use.
//
// What is NOT covered by this: the dock's burn-in drift. It moves the face by
// roughly a pixel per second, which is below the threshold anyone can perceive,
// and switching it off would trade an invisible motion for a permanently
// scorched panel.

package com.opendroid.ai.ui.face

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the user asked for, which is not always the same as what happens. */
enum class MotionSetting {
    /** Follow the device: animations stop when Android says animations are off. */
    SYSTEM,

    /** Always animate, even on a device with animations turned off globally. */
    FULL,

    /** Never animate, however the device is configured. */
    REDUCED,
}

/**
 * Whether the face may animate.
 *
 * @param systemAnimatorScale the value of `Settings.Global.ANIMATOR_DURATION_SCALE`;
 *   0f means the user has turned animations off for the whole device.
 */
fun animationsEnabled(setting: MotionSetting, systemAnimatorScale: Float): Boolean = when (setting) {
    MotionSetting.FULL -> true
    MotionSetting.REDUCED -> false
    // A negative or missing scale is not a request to stop; it is an unreadable
    // setting, and the face defaults to alive.
    MotionSetting.SYSTEM -> systemAnimatorScale != 0f
}

fun nextMotionSetting(setting: MotionSetting): MotionSetting = when (setting) {
    MotionSetting.SYSTEM -> MotionSetting.FULL
    MotionSetting.FULL -> MotionSetting.REDUCED
    MotionSetting.REDUCED -> MotionSetting.SYSTEM
}

/** Short enough to sit next to the language chip without wrapping. */
fun motionLabel(setting: MotionSetting): String = when (setting) {
    MotionSetting.SYSTEM -> "motion auto"
    MotionSetting.FULL -> "motion on"
    MotionSetting.REDUCED -> "motion off"
}

@Singleton
class MotionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _setting = MutableStateFlow(read())

    val setting: StateFlow<MotionSetting> = _setting.asStateFlow()

    fun select(value: MotionSetting) {
        if (_setting.value == value) return
        _setting.value = value
        prefs.edit().putString(KEY_MOTION, value.name).apply()
    }

    // A preference written by a build that had different names must not crash the
    // face; an unknown value simply means "follow the device".
    private fun read(): MotionSetting = runCatching {
        MotionSetting.valueOf(prefs.getString(KEY_MOTION, null) ?: return MotionSetting.SYSTEM)
    }.getOrDefault(MotionSetting.SYSTEM)

    private companion object {
        const val PREFS = "opendroid_face_appearance"
        const val KEY_MOTION = "motion"
    }
}

private fun systemAnimatorScale(context: Context): Float = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
}.getOrDefault(1f)

/**
 * True when the face should hold still.
 *
 * Watches the system setting rather than reading it once: turning animations off
 * in Settings has to take effect on a face that is already on screen, which is
 * exactly when the user is deciding whether the motion bothers them.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val store = rememberMotionStore()
    val setting by store.setting.collectAsState()
    var scale by remember { mutableFloatStateOf(systemAnimatorScale(context)) }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = systemAnimatorScale(context)
            }
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }

    return !animationsEnabled(setting, scale)
}
