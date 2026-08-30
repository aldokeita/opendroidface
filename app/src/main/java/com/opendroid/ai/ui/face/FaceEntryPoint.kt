// Bridge from Compose to Hilt for the face layer.
//
// ChatScreen builds its SpeechRecognitionEngine by hand rather than through
// injection, so the face needs a way to reach the singleton VoiceAmplitude
// without changing how the chat screen gets its dependencies. An entry point
// does that from our own code: one Hilt lookup, no upstream wiring touched.

package com.opendroid.ai.ui.face

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.opendroid.ai.core.voice.VoiceAmplitude
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FaceEntryPoint {
    fun voiceAmplitude(): VoiceAmplitude
}

/**
 * The process-wide [VoiceAmplitude]. It has to be the same instance everywhere:
 * whoever is producing amplitude (the recognizer while listening, TTS while
 * speaking) is rarely the composable that draws the face.
 */
@Composable
fun rememberVoiceAmplitude(): VoiceAmplitude {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, FaceEntryPoint::class.java).voiceAmplitude()
    }
}
