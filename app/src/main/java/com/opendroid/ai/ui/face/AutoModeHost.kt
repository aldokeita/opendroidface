// Wiring for Auto mode: microphone in, agent out.
//
// "Auto mode" is the internal name throughout this package; the user-facing
// label is "Hands-free", because upstream's Auto chip already means automatic
// plan approval.
//
// This lives in ui/face rather than in ChatScreen so the upstream file only has
// to know that Auto mode exists, not how it works. It reuses the recognizer
// ChatScreen already owns — Android allows one recognition session at a time,
// and a second engine would fight the microphone button in the chat.

package com.opendroid.ai.ui.face

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.core.voice.SpeechRecognitionEngine
import com.opendroid.ai.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay

@Composable
fun AutoModeHost(
    viewModel: ChatViewModel,
    recognizer: SpeechRecognitionEngine,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val agentState by viewModel.visibleAgentState.collectAsState()
    val amplitude by rememberVoiceAmplitude().level.collectAsState()
    val languageStore = rememberVoiceLanguageStore()
    val languageTag by languageStore.tag.collectAsState()
    val faceColorStore = rememberFaceColorStore()
    val faceColorId by faceColorStore.colorId.collectAsState()
    val motionStore = rememberMotionStore()
    val motionSetting by motionStore.setting.collectAsState()
    val transcriptStore = rememberTranscriptVisibilityStore()
    val showTranscript by transcriptStore.visible.collectAsState()

    var isListening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }
    // Dock mode. rememberSaveable so a rotation on a stand does not drop out of it.
    var kiosk by rememberSaveable { mutableStateOf(false) }
    // Attempts in a row that produced nothing - silence, or an error. Counted so
    // a refused or broken microphone stops re-arming instead of looping, and
    // reset the moment something is actually heard.
    var quietRounds by remember { mutableIntStateOf(0) }
    // The user stopped the microphone, or it gave up. Nothing reopens until they
    // ask, which is the only thing keeping "always listening" from overriding a
    // deliberate stop.
    var micPaused by remember { mutableStateOf(false) }
    // What the agent last said, kept so the caption does not blank out the
    // instant speech ends.
    var lastReply by remember { mutableStateOf("") }
    // Whether the agent spoke since the microphone was last open.
    var spokeLast by remember { mutableStateOf(false) }

    fun startListening() {
        if (isListening) return
        voiceError = null
        transcript = ""
        isListening = true
        spokeLast = false
        recognizer.startListening(
            onResult = { text ->
                isListening = false
                transcript = ""
                quietRounds = 0
                if (text.isNotBlank()) {
                    viewModel.sendMessage(text, context)
                }
            },
            onPartialResult = { partial -> transcript = partial },
            onError = { err ->
                isListening = false
                transcript = ""
                voiceError = err
                // Errors count towards the same limit as silence. Without this a
                // continuously failing recognizer and the reopen below would
                // spin against each other.
                quietRounds += 1
            },
            // Hearing nothing is what an open microphone does when nobody speaks.
            // Showing it as "No speech match found" made the screen look broken
            // every time the user paused to think.
            onNoSpeech = {
                isListening = false
                transcript = ""
                voiceError = null
                quietRounds += 1
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            voiceError = "Microphone permission is required for hands-free mode"
        }
    }

    fun requestListening() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Entering Auto mode opens the microphone straight away: the whole point of
    // the mode is that the user does not have to press anything first.
    LaunchedEffect(Unit) { requestListening() }

    // The microphone runs itself for as long as the mode is open. Only while the
    // agent is idle, so it never records the assistant's own voice, and it stops
    // once enough attempts in a row have heard nothing.
    LaunchedEffect(kiosk, agentState, isListening, quietRounds, micPaused) {
        if (shouldReopenMic(kiosk, agentState, isListening, quietRounds, micPaused)) {
            delay(reopenDelayAfter(kiosk, spokeLast))
            requestListening()
        }
    }

    // Giving up is a state the user can see and undo, not a silent stop.
    LaunchedEffect(quietRounds, kiosk) {
        if (quietRounds >= silenceLimitFor(kiosk)) micPaused = true
    }

    // Keep the last spoken answer for the caption, and remember that the last
    // thing to happen was speech so the reopen above waits for the room to go
    // quiet rather than recording the end of it.
    LaunchedEffect(agentState) {
        (agentState as? AgentState.Speaking)?.let { speaking ->
            spokeLast = true
            speaking.text.takeIf { it.isNotBlank() }?.let { lastReply = it }
        }
    }

    // Leaving the mode must not leave the recognizer holding the microphone; the
    // engine itself belongs to the chat screen, so it is cancelled, not destroyed.
    DisposableEffect(Unit) {
        onDispose {
            recognizer.cancel()
        }
    }

    // A Dialog, not an inline overlay: the chat screen sits inside the app's
    // Scaffold, so anything drawn there stops short of the bottom navigation bar.
    // Hands-free mode is supposed to be the only thing on screen.
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        )
    ) {
        AutoModeScreen(
            state = agentState,
            isListening = isListening,
            amplitude = amplitude,
            transcript = transcript,
            reply = lastReply,
            showTranscript = showTranscript,
            paused = micPaused,
            errorMessage = voiceError,
            onToggleListening = {
                if (isListening || !micPaused) {
                    // Stopping has to latch, or the reopen above would start the
                    // microphone again a moment later and the tap would look
                    // like it did nothing.
                    recognizer.cancel()
                    isListening = false
                    transcript = ""
                    micPaused = true
                } else {
                    micPaused = false
                    quietRounds = 0
                    requestListening()
                }
            },
            onClose = onExit,
            onApprovePlan = { viewModel.approvePlan(context) },
            onRejectPlan = { viewModel.rejectPlan() },
            languageLabel = voiceLanguageLabel(languageTag),
            onCycleLanguage = {
                // The recognizer reads languageTag when it builds the next session,
                // so an in-flight one is cancelled rather than left on the old
                // language.
                if (isListening) {
                    recognizer.cancel()
                    isListening = false
                    transcript = ""
                }
                languageStore.select(nextVoiceLanguage(languageTag))
            },
            faceColor = faceColorFor(faceColorId).color,
            onCycleFaceColor = { faceColorStore.select(nextFaceColor(faceColorId)) },
            motionLabel = motionLabel(motionSetting),
            onCycleMotion = { motionStore.select(nextMotionSetting(motionSetting)) },
            transcriptLabel = transcriptLabel(showTranscript),
            onToggleTranscript = { transcriptStore.toggle() },
            kiosk = kiosk,
            onEnterKiosk = {
                // The dock is entered on purpose, so it starts listening even if
                // the microphone had given up in the hand.
                quietRounds = 0
                micPaused = false
                kiosk = true
            },
            onLeaveKiosk = { kiosk = false },
            modifier = modifier,
        )
    }
}
