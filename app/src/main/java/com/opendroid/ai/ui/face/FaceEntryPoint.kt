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
import com.opendroid.ai.core.face.FaceMood
import com.opendroid.ai.core.language.AppLanguageStore
import com.opendroid.ai.core.voice.SpeechOutputStore
import com.opendroid.ai.core.voice.TtsVoiceStore
import com.opendroid.ai.core.voice.VoiceAmplitude
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FaceEntryPoint {
    fun voiceAmplitude(): VoiceAmplitude
    fun voiceLanguageStore(): VoiceLanguageStore
    fun faceColorStore(): FaceColorStore
    fun faceStyleStore(): FaceStyleStore
    fun faceMood(): FaceMood
    fun motionStore(): MotionStore
    fun transcriptVisibilityStore(): TranscriptVisibilityStore
    fun speechOutputStore(): SpeechOutputStore
    fun ttsVoiceStore(): TtsVoiceStore
    fun appLanguageStore(): AppLanguageStore
}

private fun entryPoint(context: Context): FaceEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, FaceEntryPoint::class.java)

/**
 * The process-wide [VoiceAmplitude]. It has to be the same instance everywhere:
 * whoever is producing amplitude (the recognizer while listening, TTS while
 * speaking) is rarely the composable that draws the face.
 */
@Composable
fun rememberVoiceAmplitude(): VoiceAmplitude {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).voiceAmplitude() }
}

/** The process-wide voice language selection. */
@Composable
fun rememberVoiceLanguageStore(): VoiceLanguageStore {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).voiceLanguageStore() }
}

/** The process-wide face colour selection. */
@Composable
fun rememberFaceColorStore(): FaceColorStore {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).faceColorStore() }
}

/** The process-wide face style selection. */
@Composable
fun rememberFaceStyleStore(): FaceStyleStore {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).faceStyleStore() }
}

/** The process-wide motion preference. */
@Composable
fun rememberMotionStore(): MotionStore {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).motionStore() }
}

/** Whether hands-free draws the words it is speaking. */
@Composable
fun rememberTranscriptVisibilityStore(): TranscriptVisibilityStore {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).transcriptVisibilityStore() }
}

/** Whether typed questions also get spoken answers. */
@Composable
fun rememberSpeechOutputStore(): SpeechOutputStore {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).speechOutputStore() }
}

/** The chosen Indonesian voice, shared with the speaking engine in the service. */
@Composable
fun rememberTtsVoiceStore(): TtsVoiceStore {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).ttsVoiceStore() }
}

/** The language the assistant answers and speaks in. */
@Composable
fun rememberAppLanguageStore(): AppLanguageStore {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).appLanguageStore() }
}

/** The process-wide mood, published by the agent. */
@Composable
fun rememberFaceMood(): FaceMood {
    val context: Context = LocalContext.current.applicationContext
    return remember(context) { entryPoint(context).faceMood() }
}
