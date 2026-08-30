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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.core.voice.SpeechRecognitionEngine
import com.opendroid.ai.ui.viewmodel.ChatViewModel

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

    var isListening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }
    // Auto-restart is deliberately one-shot per answer: it re-arms only after the
    // agent has actually produced something. Restarting on every Idle would put
    // the microphone in a loop with its own recognition errors.
    var awaitingAnswer by remember { mutableStateOf(false) }

    fun startListening() {
        if (isListening) return
        voiceError = null
        transcript = ""
        isListening = true
        recognizer.startListening(
            onResult = { text ->
                isListening = false
                transcript = ""
                if (text.isNotBlank()) {
                    awaitingAnswer = true
                    viewModel.sendMessage(text, context)
                }
            },
            onPartialResult = { partial -> transcript = partial },
            onError = { err ->
                isListening = false
                transcript = ""
                voiceError = err
            }
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

    // Hands-free turn taking. The mic reopens only once the agent has gone quiet
    // after answering, so it never records the assistant's own speech.
    LaunchedEffect(agentState, isListening) {
        if (agentState is AgentState.Idle && awaitingAnswer && !isListening) {
            awaitingAnswer = false
            requestListening()
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
            errorMessage = voiceError,
            onToggleListening = {
                if (isListening) {
                    recognizer.cancel()
                    isListening = false
                    transcript = ""
                } else {
                    requestListening()
                }
            },
            onClose = onExit,
            onApprovePlan = { viewModel.approvePlan(context) },
            onRejectPlan = { viewModel.rejectPlan() },
            modifier = modifier,
        )
    }
}
