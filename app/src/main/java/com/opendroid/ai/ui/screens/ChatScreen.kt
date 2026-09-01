package com.opendroid.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.opendroid.ai.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.core.agent.AutoApprovalPolicy
import com.opendroid.ai.core.agent.ChatErrorPrimaryAction
import com.opendroid.ai.core.agent.ChatErrorUiState
import com.opendroid.ai.core.agent.guidance
import com.opendroid.ai.core.agent.primaryAction
import com.opendroid.ai.core.agent.title
import com.opendroid.ai.core.voice.SpeechRecognitionEngine
import com.opendroid.ai.data.models.AutoMode
import com.opendroid.ai.data.models.ChatMessage
import com.opendroid.ai.data.models.effectiveGrantedActions
import com.opendroid.ai.data.models.resolvedAutoMode
import com.opendroid.ai.data.repository.ChatSession
import com.opendroid.ai.ui.components.ContactPickerCard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.opendroid.ai.ui.bridge.DesktopBridgeScreen
import com.opendroid.ai.ui.face.AutoModeButton
import com.opendroid.ai.ui.face.AutoModeHost
import com.opendroid.ai.ui.face.RobotFace
import com.opendroid.ai.ui.face.faceStateFor
import com.opendroid.ai.ui.face.rememberVoiceAmplitude
import com.opendroid.ai.ui.face.rememberVoiceLanguageStore
import com.opendroid.ai.ui.theme.*
import com.opendroid.ai.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The palette rather than the top-level dark aliases: this screen used the
    // static dark values, which painted a dark-theme chat on a light background.
    val colors = LocalOpenDroidColors.current
    val history by viewModel.conversationHistory.collectAsState()
    // Scoped to whichever chat is on screen right now - a task that's actually running
    // in a DIFFERENT chat (safe since the pinned-session fix: it keeps writing there,
    // not wherever the user has navigated to) must never be displayed as if it were
    // happening here. See ChatViewModel.visibleAgentState.
    val visibleAgentState by viewModel.visibleAgentState.collectAsState()
    val chatError by viewModel.chatError.collectAsState()
    // Id of whichever chat (if any) has a task actively running, regardless of which
    // chat is currently displayed - drives the chat-picker's "still running" indicator.
    val runningSessionId by viewModel.runningSessionId.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val llmConfig by viewModel.llmConfig.collectAsState()
    val currentSessionId = sessions.firstOrNull { it.isCurrent }?.id
    val runningElsewhere = runningSessionId != null && runningSessionId != currentSessionId

    val listState = rememberLazyListState()
    var inputQuery by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var transcriptionText by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var showChatMenu by remember { mutableStateOf(false) }
    var sessionPendingDelete by remember { mutableStateOf<ChatSession?>(null) }
    var sessionPendingRename by remember { mutableStateOf<ChatSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var textBeforeEdit by remember { mutableStateOf("") }
    // Voice-only face mode. Kept in the composable rather than in ChatViewModel:
    // it is a view concern and nothing outside this screen needs to know about it.
    var autoModeActive by rememberSaveable { mutableStateOf(false) }
    // The MCP bridge screen: the access token, and the switch that decides which
    // interface the server listens on.
    var showDesktopBridge by rememberSaveable { mutableStateOf(false) }

    // Merges a piece of dictated text into whatever the user already typed, so review/edit
    // never clobbers text entered before dictation started.
    fun mergeDictatedText(newText: String) {
        if (newText.isBlank()) return
        inputQuery = if (inputQuery.isBlank()) {
            newText
        } else {
            inputQuery.trimEnd() + " " + newText.trimStart()
        }
    }

    // Loads a previously sent user message into the input field for editing, remembering
    // whatever was already typed so cancelling the edit can restore it untouched.
    fun startEditingMessage(message: ChatMessage) {
        if (editingMessageId == null) {
            textBeforeEdit = inputQuery
        }
        editingMessageId = message.id
        inputQuery = message.text
    }

    // Restores the input to whatever it held before the edit started; the conversation
    // itself is never touched until a resend is actually submitted.
    fun cancelEditingMessage() {
        editingMessageId = null
        inputQuery = textBeforeEdit
        textBeforeEdit = ""
    }

    // Single submit path for both a normal send and an edit-resend, wired to both the
    // keyboard "Send" action and the send button below.
    fun submitInput() {
        val text = inputQuery
        if (text.isBlank()) return
        val editing = editingMessageId
        if (editing != null) {
            viewModel.editAndResend(editing, text, context)
            editingMessageId = null
            textBeforeEdit = ""
        } else {
            viewModel.sendMessage(text, context)
        }
        inputQuery = ""
    }

    // Scroll to bottom on history change. Keyed on currentSessionId too: keying on
    // history.size alone left the scroll position from the PREVIOUS chat in place
    // whenever the newly switched-to chat happened to have the same message count -
    // including currentSessionId forces a re-anchor to the bottom on every chat switch.
    LaunchedEffect(currentSessionId, history.size, visibleAgentState) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(history.size - 1)
        }
    }

    // Shared microphone level, so the face can react to the user's voice.
    val voiceAmplitude = rememberVoiceAmplitude()
    val amplitude by voiceAmplitude.level.collectAsState()
    val speechRecognizer = remember { SpeechRecognitionEngine(context, voiceAmplitude) }
    // Recognition language, shared with hands-free mode; null follows the device.
    val voiceLanguageTag by rememberVoiceLanguageStore().tag.collectAsState()
    LaunchedEffect(voiceLanguageTag) { speechRecognizer.languageTag = voiceLanguageTag }
    
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isListening = true
            voiceError = null
            transcriptionText = ""
            speechRecognizer.startListening(
                onResult = { text ->
                    isListening = false
                    mergeDictatedText(text)
                    transcriptionText = ""
                },
                onPartialResult = { partial ->
                    transcriptionText = partial
                },
                onError = { err ->
                    isListening = false
                    mergeDictatedText(transcriptionText)
                    transcriptionText = ""
                    voiceError = err
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // The wordmark, set rather than shouted: neon monospace at
                        // 2sp tracking read as a terminal banner, which is the one
                        // thing a minimal chat screen should not open with.
                        Text(
                            text = "OpenDroid",
                            fontFamily = Montserrat,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 19.sp,
                            letterSpacing = (-0.3).sp
                        )
                        AgentStatusSubtitle(visibleAgentState, runningElsewhere)
                    }
                },
                actions = {
                    val autoMode = llmConfig.resolvedAutoMode()
                    val chipColor = when (autoMode) {
                        AutoMode.OFF -> colors.textSecondary
                        AutoMode.AUTO -> colors.accentNeonGreen
                        AutoMode.YOLO -> colors.accentRed
                    }
                    // A tinted label, not an outlined button. The approval mode is
                    // a state to glance at; only YOLO earns a visible container,
                    // because that one changes what a tap can do to the phone.
                    Text(
                        text = when (autoMode) {
                            AutoMode.OFF -> "MANUAL"
                            AutoMode.AUTO -> "AUTO"
                            AutoMode.YOLO -> "YOLO"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = chipColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (autoMode == AutoMode.YOLO) chipColor.copy(alpha = 0.14f)
                                else Color.Transparent
                            )
                            .clickable { viewModel.cycleAutoMode() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    IconButton(onClick = { showDesktopBridge = true }) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = stringResource(R.string.chat_desktop_bridge),
                            tint = colors.textSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.newChat() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.chat_new),
                            tint = colors.textSecondary
                        )
                    }
                    Box {
                        IconButton(onClick = { showChatMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = stringResource(R.string.chat_history),
                                tint = colors.textSecondary
                            )
                        }
                        DropdownMenu(
                            expanded = showChatMenu,
                            onDismissRequest = { showChatMenu = false },
                            modifier = Modifier.background(colors.surface)
                        ) {
                            if (sessions.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_none_yet), color = TextSecondary, fontSize = 13.sp) },
                                    onClick = {},
                                    enabled = false
                                )
                            }
                            sessions.forEach { session ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = session.title,
                                                color = if (session.isCurrent) AccentNeonGreen else TextPrimary,
                                                fontWeight = if (session.isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                maxLines = 1
                                            )
                                            // Small "still running" dot: a task can now keep
                                            // executing in a chat the user has switched away
                                            // from, so this is the only place that fact is
                                            // visible once its row scrolls out of the top bar.
                                            if (runningSessionId == session.id) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(AccentNeonGreen)
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = if (session.isCurrent) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = AccentNeonGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    renameText = session.title
                                                    sessionPendingRename = session
                                                    showChatMenu = false
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = stringResource(R.string.chat_rename),
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    sessionPendingDelete = session
                                                    showChatMenu = false
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.chat_delete),
                                                    tint = AccentRed,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        showChatMenu = false
                                        viewModel.switchToSession(session.id)
                                    }
                                )
                            }
                            // Clearing the conversation lived in the top bar as a
                            // fifth control competing with four others. It belongs
                            // with the rest of the chat management, one tap deeper.
                            HorizontalDivider(color = colors.borderColor.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.chat_clear),
                                        color = colors.accentRed,
                                        fontSize = 13.sp,
                                    )
                                },
                                onClick = {
                                    showChatMenu = false
                                    viewModel.clearChat()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                )
            )
        },
        containerColor = colors.background,
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            ) {
                // Only one recognition session can be open at a time, so the
                // microphone is handed over cleanly before Auto mode claims it.
                val enterHandsFree = {
                    if (isListening) {
                        speechRecognizer.cancel()
                        isListening = false
                        transcriptionText = ""
                    }
                    autoModeActive = true
                }

                // Once a conversation exists the face becomes a small header and
                // the messages get the screen. An empty chat has no header at all:
                // the greeting below fills it instead, so the screen opens on one
                // centred thing rather than on a band of face above a void.
                if (history.isNotEmpty()) {
                    // A header ROW, not a band with a face floating in the middle
                    // of it: at this size the face is an avatar, and an avatar
                    // belongs at the edge next to the control it shares a line with.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RobotFace(
                            state = faceStateFor(visibleAgentState, micOpen = isListening),
                            amplitude = amplitude,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        AutoModeButton(onClick = enterHandsFree)
                    }
                }

                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                ) {
                    if (history.isEmpty()) {
                        item {
                            EmptyChatHero(
                                state = faceStateFor(visibleAgentState, micOpen = isListening),
                                amplitude = amplitude,
                                onHandsFree = enterHandsFree,
                                modifier = Modifier.fillParentMaxHeight(0.92f)
                            )
                        }
                    }

                    items(history) { msg ->
                        ChatBubble(
                            message = msg,
                            viewModel = viewModel,
                            context = context,
                            onEditRequested = { startEditingMessage(it) }
                        )
                    }
                    
                    // Show a typing/thinking bubble if thinking - scoped to this chat, see
                    // visibleAgentState.
                    if (visibleAgentState is AgentState.Thinking) {
                        item {
                            ThinkingBubble()
                        }
                    }

                    chatError?.let { error ->
                        item(key = "chat-error-${error.requestId}-${error.runId}") {
                            ChatErrorRecoveryCard(
                                error = error,
                                onPrimary = {
                                    when (error.primaryAction()) {
                                        ChatErrorPrimaryAction.RETRY ->
                                            viewModel.retryAfterChatError(context)
                                        ChatErrorPrimaryAction.EDIT_MESSAGE -> {
                                            // Edit the exact message the error is about;
                                            // fall back to the last user message only if
                                            // the requestId no longer resolves to one.
                                            val target = history.firstOrNull {
                                                it.id == error.requestId &&
                                                    it.sender == ChatMessage.Sender.USER
                                            } ?: history.lastOrNull {
                                                it.sender == ChatMessage.Sender.USER
                                            }
                                            target?.let { startEditingMessage(it) }
                                            viewModel.dismissChatError()
                                        }
                                        else -> viewModel.dismissChatError()
                                    }
                                },
                                onDismiss = { viewModel.dismissChatError() }
                            )
                        }
                    }
                }

                // If agent proposed a plan for THIS chat, show a modal prompt to approve or
                // reject. A plan proposed for a different chat must never surface here - the
                // user could approve/reject the wrong chat's plan without realizing it.
                if (visibleAgentState is AgentState.PlanProposed) {
                    val proposedPlan = (visibleAgentState as AgentState.PlanProposed).plan
                    val blocked = if (llmConfig.resolvedAutoMode() == AutoMode.AUTO) {
                        AutoApprovalPolicy.blockedActions(
                            llmConfig.effectiveGrantedActions().keys, proposedPlan.steps
                        )
                    } else emptyList()
                    ProposedPlanPrompt(
                        planId = proposedPlan.planId,
                        goal = proposedPlan.goal,
                        stepsCount = proposedPlan.estimatedSteps,
                        blockedActions = blocked,
                        grantableActions = blocked.filter { AutoApprovalPolicy.isGrantable(it) }.toSet(),
                        onApprove = { grants -> viewModel.approvePlan(context, grants) },
                        onReject = { viewModel.rejectPlan() }
                    )
                }
            }

            // Bottom Input Section with Orb overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // A scrim rather than a hard edge: messages fade out under the
                    // composer instead of being cut off by a band of background.
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, colors.background),
                            startY = 0f,
                            endY = 90f
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (editingMessageId != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.chat_editing),
                                fontSize = 11.sp,
                                color = AccentCyan,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.chat_cancel_edit),
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { cancelEditingMessage() }
                            )
                        }
                    }
                    if (voiceError != null) {
                        Text(
                            text = voiceError.orEmpty(),
                            fontSize = 11.sp,
                            color = AccentRed,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Floating Orb for Speech
                        FloatingOrb(
                            isListening = isListening,
                            agentState = visibleAgentState,
                            onClick = {
                                if (isListening) {
                                    // True cancel: no final result will be delivered for this
                                    // session, so whatever partial transcript we already have is
                                    // handed back to the input field instead of being lost.
                                    speechRecognizer.cancel()
                                    isListening = false
                                    mergeDictatedText(transcriptionText)
                                    transcriptionText = ""
                                } else {
                                    val audioPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                    if (audioPerm == PackageManager.PERMISSION_GRANTED) {
                                        isListening = true
                                        voiceError = null
                                        transcriptionText = ""
                                        speechRecognizer.startListening(
                                            onResult = { text ->
                                                isListening = false
                                                mergeDictatedText(text)
                                                transcriptionText = ""
                                            },
                                            onPartialResult = { partial ->
                                                transcriptionText = partial
                                            },
                                            onError = { err ->
                                                isListening = false
                                                mergeDictatedText(transcriptionText)
                                                transcriptionText = ""
                                                voiceError = err
                                            }
                                        )
                                    } else {
                                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Text Input Field / Voice Waveform Area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp, max = 120.dp)
                                .clip(RoundedCornerShape(26.dp))
                                // The same near-black as the nav bar. The composer
                                // and the bar sit one above the other, so a field
                                // a shade lighter than the bar under it read as two
                                // unrelated surfaces stacked up.
                                .background(colors.surface)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (isListening) {
                                VoiceWaveform(
                                    text = transcriptionText,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(vertical = 12.dp)
                                )
                            } else {
                                TextField(
                                    value = inputQuery,
                                    onValueChange = { inputQuery = it; voiceError = null },
                                    placeholder = {
                                        Text(
                                            stringResource(R.string.chat_input_hint),
                                            color = colors.textSecondary,
                                            fontSize = 15.sp,
                                        )
                                    },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary,
                                        cursorColor = colors.accentNeonGreen,
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { submitInput() }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (!isListening && inputQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { submitInput() },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentGreenButton)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.chat_send),
                                    tint = colors.background,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Hands-free mode covers the whole screen, bottom navigation included; it
    // hosts itself in a Dialog, so Back is handled there too.
    // Same Dialog trick as hands-free mode: this screen sits inside the app's
    // Scaffold, and the bridge screen wants the whole display.
    if (showDesktopBridge) {
        Dialog(
            onDismissRequest = { showDesktopBridge = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            DesktopBridgeScreen(onClose = { showDesktopBridge = false })
        }
    }

    if (autoModeActive) {
        AutoModeHost(
            viewModel = viewModel,
            recognizer = speechRecognizer,
            onExit = { autoModeActive = false }
        )
    }

    sessionPendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            containerColor = DarkSurface,
            title = { Text(stringResource(R.string.chat_delete_confirm), color = TextPrimary) },
            text = {
                Text(
                    "\"${session.title}\" and its messages will be permanently deleted.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChat(session.id)
                    sessionPendingDelete = null
                }) {
                    Text(stringResource(R.string.common_delete), color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel), color = TextSecondary)
                }
            }
        )
    }

    sessionPendingRename?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionPendingRename = null },
            containerColor = DarkSurface,
            title = { Text(stringResource(R.string.chat_rename), color = TextPrimary) },
            text = {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = AccentNeonGreen,
                        unfocusedIndicatorColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameSession(session.id, renameText)
                    sessionPendingRename = null
                }) {
                    Text(stringResource(R.string.common_save), color = AccentNeonGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingRename = null }) {
                    Text(stringResource(R.string.common_cancel), color = TextSecondary)
                }
            }
        )
    }
}

/**
 * What an empty chat opens on: the face, a greeting, and the one control worth
 * offering before anything has been said.
 *
 * It fills the message list rather than sitting above it, so the whole thing is
 * optically centred instead of stacked under the top bar with the rest of the
 * screen left blank.
 */
@Composable
private fun EmptyChatHero(
    state: AgentState,
    amplitude: Float,
    onHandsFree: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOpenDroidColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RobotFace(
            state = state,
            amplitude = amplitude,
            // The face draws inside the middle of whatever box it is given, so a
            // 200dp box put a hand's width of nothing between the eyes and the
            // greeting they belong to.
            modifier = Modifier.size(150.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.chat_greeting_title),
            fontFamily = Montserrat,
            fontWeight = FontWeight.SemiBold,
            fontSize = 23.sp,
            letterSpacing = (-0.3).sp,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chat_greeting_subtitle),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        AutoModeButton(onClick = onHandsFree)
    }
}

@Composable
fun AgentStatusSubtitle(state: AgentState, runningElsewhere: Boolean = false) {
    // runningElsewhere means [state] has already been forced to Idle because the real
    // activity belongs to a different chat (see ChatViewModel.visibleAgentState) - say
    // so explicitly instead of showing a plain "Online & Ready" that would hide the
    // fact that a task is still going in the background.
    val text = if (runningElsewhere) {
        "Online & Ready · Task running in another chat"
    } else {
        when (state) {
            is AgentState.Idle -> "Online & Ready"
            is AgentState.Listening -> "Listening to voice input..."
            is AgentState.Thinking -> "Analyzing intent & planning..."
            is AgentState.PlanProposed -> "Requires Plan Approval"
            is AgentState.ExecutingPlan -> "Executing: ${state.currentStepDesc}"
            is AgentState.Speaking -> "Speaking: ${state.text.take(30)}..."
            is AgentState.Error -> "Execution Error"
        }
    }

    val colors = LocalOpenDroidColors.current
    val color = if (runningElsewhere) {
        colors.accentPurple
    } else {
        when (state) {
            is AgentState.Idle -> colors.accentNeonGreen
            is AgentState.Listening -> colors.accentRed
            is AgentState.Thinking -> colors.accentPurple
            is AgentState.PlanProposed -> colors.accentCyan
            is AgentState.ExecutingPlan -> colors.accentNeonGreen
            is AgentState.Speaking -> colors.accentCyan
            is AgentState.Error -> colors.accentRed
        }
    }

    // The colour moves to a 6dp dot and the words stay neutral. A whole line of
    // neon green under the wordmark was the loudest thing on the screen, and it
    // said "idle".
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    viewModel: ChatViewModel? = null,
    context: android.content.Context? = null,
    onEditRequested: ((ChatMessage) -> Unit)? = null
) {
    val colors = LocalOpenDroidColors.current
    val isAgent = message.sender == ChatMessage.Sender.AGENT
    val alignment = if (isAgent) Alignment.Start else Alignment.End
    // Two flat fills and no outlines. Every bubble used to carry a 1dp border, so
    // a screen of six messages drew twelve competing rectangles; the fill alone
    // separates them, and the purple tint is enough to say who is speaking.
    val bubbleColor = if (isAgent) colors.cardBackground else colors.accentPurple.copy(alpha = 0.18f)
    val textColor = colors.textPrimary
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isAgent) 6.dp else 20.dp,
        bottomEnd = if (isAgent) 20.dp else 6.dp,
    )

    // If this is a contact picker message, render the ContactPickerCard instead
    if (isAgent && message.contactPickerData != null) {
        val matches: List<Map<String, String>> = try {
            Json { ignoreUnknownKeys = true }
                .decodeFromString<List<Map<String, String>>>(message.contactPickerData)
        } catch (_: Exception) {
            emptyList()
        }

        if (matches.isNotEmpty()) {
            // Extract query from text ("Which 'dad' do you mean?" ? "dad")
            val query = Regex("Which '(.*?)'").find(message.text)?.groupValues?.getOrNull(1) ?: "contact"

            ContactPickerCard(
                query = query,
                matches = matches,
                onContactSelected = { selected ->
                    val index = matches.indexOf(selected) + 1
                    if (viewModel != null && context != null) {
                        viewModel.sendMessage(index.toString(), context)
                    }
                }
            )
            return
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isAgent) Arrangement.Start else Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isAgent && message.modelBadge != null) {
                    val displayName = when (message.modelBadge) {
                        "Gemma 4 (On-device)" -> "ON-DEVICE (AI CORE)"
                        "On-Device AI" -> "ON-DEVICE AI"
                        "LiteRT-LM (On-device)" -> "ON-DEVICE (LITERT)"
                        else -> message.modelBadge.uppercase(Locale.getDefault())
                    }
                    Text(
                        text = displayName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = colors.accentCyan.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Text(
                    text = inlineMarkdown(message.text),
                    fontSize = 15.sp,
                    color = textColor,
                    lineHeight = 23.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Edit affordance: user messages only, never on agent replies or the
                    // contact-picker card (which is always an agent message, so it's
                    // already excluded by the isAgent check above never reaching here).
                    if (!isAgent && onEditRequested != null) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.chat_edit_message),
                            tint = colors.textSecondary.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(13.dp)
                                .clickable { onEditRequested(message) }
                        )
                    }
                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        fontSize = 10.sp,
                        color = colors.textSecondary.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun ThinkingBubble() {
    val colors = LocalOpenDroidColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
                .background(colors.cardBackground)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Three dots that rise in sequence, instead of one block fading in and
            // out as a unit. A whole bubble pulsing reads as a rendering fault; a
            // travelling wave reads as waiting.
            repeat(3) { index ->
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = index * 180, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot$index"
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.textSecondary.copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
fun ProposedPlanPrompt(
    planId: String,
    goal: String,
    stepsCount: Int,
    blockedActions: List<String> = emptyList(),
    grantableActions: Set<String> = emptySet(),
    onApprove: (Set<String>) -> Unit,
    onReject: () -> Unit
) {
    // Keyed on both: `planId` because a new plan must not inherit the previous
    // plan's ticked grants even when the two happen to block the same actions,
    // and `blockedActions` because the checkboxes are drawn from that list, so a
    // change to it would otherwise leave ticks referring to rows that are gone.
    var checkedGrants by remember(planId, blockedActions) { mutableStateOf(setOf<String>()) }
    val colors = LocalOpenDroidColors.current

    // The card arrives while the user is reading. A short rise and fade marks
    // that something now wants an answer, without a bounce that would pull the
    // eye away from the sentence it is asking about.
    val entry = remember(planId) { Animatable(0f) }
    LaunchedEffect(planId) {
        entry.animateTo(1f, animationSpec = tween(260, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .graphicsLayer {
                alpha = entry.value
                translationY = (1f - entry.value) * 18.dp.toPx()
            }
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentNeonGreen.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        // A label, not an alarm. The old card led with a warning triangle and
        // AUTONOMOUS PLAN PROPOSED in bold caps, which shouted at the user
        // about something they had just asked for themselves.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.plan_needs_approval),
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Medium,
                color = colors.accentNeonGreen,
            )
            Text(
                text = if (stepsCount == 1) "1 step" else "$stepsCount steps",
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                color = colors.textSecondary,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // The goal, in the user's own words, is the whole question. It used to
        // sit at 14sp under a heading, below a paragraph explaining what a plan
        // is - so the one thing being decided was the smallest thing on screen.
        Text(
            text = goal,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Light,
            color = colors.textPrimary,
        )

        if (blockedActions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = colors.borderColor.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.plan_not_allowlisted),
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Medium,
                color = colors.accentOrange,
            )
            Spacer(modifier = Modifier.height(10.dp))
            blockedActions.forEach { action ->
                val grantable = action in grantableActions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (grantable) {
                                Modifier.clickable {
                                    checkedGrants = if (action in checkedGrants) {
                                        checkedGrants - action
                                    } else {
                                        checkedGrants + action
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = action.asActionLabel(),
                            fontSize = 13.sp,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = if (grantable) "Remember this choice" else "Always asks",
                            fontSize = 10.sp,
                            color = colors.textSecondary,
                        )
                    }
                    if (grantable) {
                        Switch(
                            checked = action in checkedGrants,
                            onCheckedChange = { checked ->
                                checkedGrants = if (checked) checkedGrants + action else checkedGrants - action
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.accentNeonGreen,
                                checkedTrackColor = colors.accentNeonGreen.copy(alpha = 0.5f),
                            ),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Equal width, because this is a choice and not a form with one obvious
        // way out. Approve is the filled one; nothing here is destructive
        // enough for Reject to be the loud colour.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onReject,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, colors.borderColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
            ) {
                Text(stringResource(R.string.plan_reject), fontSize = 14.sp)
            }
            Button(
                onClick = { onApprove(checkedGrants) },
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentGreenButton,
                    contentColor = colors.background,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
            ) {
                Text(stringResource(R.string.plan_approve), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Draws the emphasis models write instead of printing its punctuation.
 *
 * Nothing rendered Markdown here, so an answer arrived as `**Selesai!**` and
 * the asterisks sat on screen as literal characters. This is the smallest
 * useful subset - bold, italic, inline code - done in one pass; anything more
 * (headings, lists, tables) is a document format, and chat bubbles are not
 * documents.
 */
private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val match = INLINE_MARKUP.find(text, index)
        if (match == null) {
            append(text.substring(index))
            return@buildAnnotatedString
        }
        append(text.substring(index, match.range.first))

        val (marker, content) = when {
            match.groupValues[1].isNotEmpty() -> "***" to match.groupValues[1]
            match.groupValues[2].isNotEmpty() -> "**" to match.groupValues[2]
            match.groupValues[3].isNotEmpty() -> "*" to match.groupValues[3]
            else -> "`" to match.groupValues[4]
        }
        val style = when (marker) {
            "***" -> SpanStyle(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)
            "**" -> SpanStyle(fontWeight = FontWeight.SemiBold)
            "*" -> SpanStyle(fontStyle = FontStyle.Italic)
            else -> SpanStyle(fontFamily = FontFamily.Monospace)
        }
        withStyle(style) { append(content) }
        index = match.range.last + 1
    }
}

/** Longest marker first, so `***both***` is not read as bold plus a stray star. */
private val INLINE_MARKUP = Regex(
    """\*\*\*(.+?)\*\*\*|\*\*(.+?)\*\*|\*(.+?)\*|`([^`]+)`""",
    RegexOption.DOT_MATCHES_ALL,
)

/** `SEND_MESSAGE` reads as shouting; `Send message` reads as a thing it does. */
private fun String.asActionLabel(): String =
    lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
fun FloatingOrb(
    isListening: Boolean,
    agentState: AgentState,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val colorPulse by animateColorAsState(
        targetValue = when {
            isListening -> AccentRed
            agentState is AgentState.Thinking -> AccentPurple
            agentState is AgentState.ExecutingPlan -> AccentNeonGreen
            agentState is AgentState.Speaking -> AccentCyan
            else -> BorderColor
        },
        animationSpec = tween(500),
        label = "color"
    )

    val shadowSize = if (isListening || agentState !is AgentState.Idle) pulseScale else 1f

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(shadowSize)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(colorPulse, Color.Transparent),
                    radius = 120f
                )
            )
            .clickable { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colorPulse, colorPulse.copy(alpha = 0.6f))
                    )
                )
                .border(2.dp, TextPrimary.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(DarkBackground)
            )
        }
    }
}

@Composable
fun VoiceWaveform(text: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val heightScale1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val heightScale2 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = 100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val heightScale3 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.width(60.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(4.dp).height(heightScale1.dp).clip(CircleShape).background(AccentRed))
            Box(modifier = Modifier.width(4.dp).height(heightScale2.dp).clip(CircleShape).background(AccentRed))
            Box(modifier = Modifier.width(4.dp).height(heightScale3.dp).clip(CircleShape).background(AccentRed))
            Box(modifier = Modifier.width(4.dp).height(heightScale2.dp).clip(CircleShape).background(AccentRed))
            Box(modifier = Modifier.width(4.dp).height(heightScale1.dp).clip(CircleShape).background(AccentRed))
        }
        Spacer(modifier = Modifier.width(8.dp))
        // No maxLines cap - long dictation wraps across multiple lines and the container
        // (see the input Box in ChatScreen) scrolls once it exceeds its bounded max height.
        Text(
            text = text,
            fontSize = 13.sp,
            color = TextPrimary,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChatErrorRecoveryCard(
    error: ChatErrorUiState,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit
) {
    var detailsExpanded by remember { mutableStateOf(false) }
    // Countdown for rate-limited errors: while the provider's retry-after window is
    // open, the Retry button is disabled and the remaining seconds tick down here.
    val phase = error.phase
    var waitSecondsLeft by remember(phase) {
        mutableLongStateOf(
            if (phase is ChatErrorUiState.Phase.WaitingUntil) {
                ((phase.epochMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            } else {
                0L
            }
        )
    }
    if (phase is ChatErrorUiState.Phase.WaitingUntil) {
        LaunchedEffect(phase) {
            while (true) {
                val remainingMillis = phase.epochMillis - System.currentTimeMillis()
                waitSecondsLeft = (remainingMillis / 1000L).coerceAtLeast(0L)
                if (remainingMillis <= 0L) break
                delay(1000L)
            }
        }
    }
    val retryHeld = phase is ChatErrorUiState.Phase.Retrying ||
        (phase is ChatErrorUiState.Phase.WaitingUntil && waitSecondsLeft > 0L)
    val actionLabel = when (error.primaryAction()) {
        ChatErrorPrimaryAction.OPEN_SETTINGS -> "Open Settings"
        ChatErrorPrimaryAction.CHOOSE_PROVIDER -> "Choose provider"
        ChatErrorPrimaryAction.CHOOSE_MODEL -> "Choose model"
        ChatErrorPrimaryAction.EDIT_MESSAGE -> "Edit message"
        ChatErrorPrimaryAction.RETRY -> "Retry"
        ChatErrorPrimaryAction.NONE -> null
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AccentRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = error.title(),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            if (error.partialMessageId != null) {
                Text(
                    text = stringResource(R.string.chat_incomplete),
                    color = AccentCyan,
                    fontSize = 11.sp,
                )
            }
            Text(text = error.guidance(), color = TextSecondary, fontSize = 13.sp)
            if (phase is ChatErrorUiState.Phase.WaitingUntil && waitSecondsLeft > 0L) {
                Text(
                    text = "Retry available in ${waitSecondsLeft}s",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (actionLabel != null) {
                    Button(
                        onClick = onPrimary,
                        enabled = !(retryHeld && error.primaryAction() == ChatErrorPrimaryAction.RETRY),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentNeonGreen,
                            contentColor = DarkBackground
                        ),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(actionLabel)
                    }
                }
                TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                    Text(if (detailsExpanded) "Hide details" else "Technical details")
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_dismiss)) }
            }
            if (detailsExpanded) {
                val detail = buildString {
                    append(error.category.code)
                    append(" · ")
                    append(error.provider)
                    error.httpStatus?.let { append(" · HTTP "); append(it) }
                    error.model.takeIf { it.isNotBlank() }?.let { append(" · "); append(it) }
                    error.redactedDetail?.toString()?.takeIf { it.isNotBlank() }?.let {
                        append(" · ")
                        append(it)
                    }
                }
                Text(
                    text = detail,
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}


