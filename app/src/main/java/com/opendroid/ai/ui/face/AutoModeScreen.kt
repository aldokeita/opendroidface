// Full-screen, voice-only mode: the robot face IS the interface.
//
// Nothing here talks to the agent or to the recognizer — every input is a
// callback and every output is a parameter. AutoModeHost owns that wiring, so
// this file stays previewable and its status text stays unit-testable.
//
// Layout rules this screen follows:
//   - The background is pure black, not the app's near-black. On an OLED panel
//     those pixels are switched off, so the face appears to float rather than
//     sit on a dark rectangle.
//   - The whole screen is the microphone button. A circular button competed
//     with the face for attention and pushed everything else off-centre.
//   - Status is one quiet line under the face; the transcript below it is the
//     loud one, because the words are what the user is checking.

package com.opendroid.ai.ui.face

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.ui.theme.LocalOpenDroidColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * One short line telling the user what is happening. In Auto mode this is the
 * only text on screen, so it has to carry the whole status by itself.
 *
 * [isListening] is the microphone's own state, which leads the agent's: the mic
 * is already open while the agent is still Idle.
 */
fun autoModeStatusLabel(
    state: AgentState,
    isListening: Boolean,
    paused: Boolean = false,
): String = when {
    isListening -> "Listening…"
    state is AgentState.Thinking -> "Thinking…"
    state is AgentState.PlanProposed -> "Waiting for your approval"
    state is AgentState.ExecutingPlan -> state.currentStepDesc.ifBlank { "Working on it…" }
    state is AgentState.Speaking -> "Speaking…"
    state is AgentState.Error -> "Something went wrong"
    state is AgentState.Listening -> "Listening…"
    // Only reachable now that the user stopped the microphone or it gave up.
    // While the mode is running the microphone reopens on its own, so inviting
    // a tap would be describing a screen that does not need one.
    paused -> "Tap anywhere to speak"
    else -> "Listening…"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoModeScreen(
    state: AgentState,
    isListening: Boolean,
    transcript: String,
    errorMessage: String?,
    onToggleListening: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** The agent's last spoken answer, drawn only when [showTranscript]. */
    reply: String = "",
    showTranscript: Boolean = true,
    /** The microphone is stopped and will not reopen by itself. */
    paused: Boolean = false,
    transcriptLabel: String? = null,
    onToggleTranscript: (() -> Unit)? = null,
    amplitude: Float = 0f,
    onApprovePlan: (() -> Unit)? = null,
    onRejectPlan: (() -> Unit)? = null,
    languageLabel: String? = null,
    onCycleLanguage: (() -> Unit)? = null,
    faceColor: Color? = null,
    onCycleFaceColor: (() -> Unit)? = null,
    motionLabel: String? = null,
    onCycleMotion: (() -> Unit)? = null,
    /** Dock mode: no controls, screen kept awake, microphone re-arming itself. */
    kiosk: Boolean = false,
    onEnterKiosk: (() -> Unit)? = null,
    onLeaveKiosk: (() -> Unit)? = null,
) {
    val colors = LocalOpenDroidColors.current
    val awaitingApproval = state is AgentState.PlanProposed
    var showGallery by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()

    if (showGallery) {
        FaceGallery(onClose = { showGallery = false }, modifier = modifier)
        return
    }

    // A docked phone is meant to be watched, not to fall asleep mid-sentence.
    val view = LocalView.current
    DisposableEffect(kiosk) {
        view.keepScreenOn = kiosk
        onDispose { view.keepScreenOn = false }
    }

    // Slow, wide travel across the panel so no pixel stays lit for hours. Only in
    // the dock: it is the only mode a phone stays on this screen long enough to
    // matter.
    var driftX by remember { mutableFloatStateOf(0f) }
    var driftY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(kiosk) {
        if (!kiosk) {
            driftX = 0f
            driftY = 0f
            return@LaunchedEffect
        }
        var elapsed = 0L
        while (true) {
            val (x, y) = kioskDrift(elapsed)
            driftX = x
            driftY = y
            delay(2_000)
            elapsed += 2_000
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // The screen itself is the button. Approval is the one moment it is
            // not: a tap meant for "yes" must never be swallowed as "start
            // listening".
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !awaitingApproval,
                // In the dock the microphone runs itself, so a tap has nothing to
                // toggle; a long press is the way out, since there are no controls
                // left to aim at.
                onClick = { if (!kiosk) onToggleListening() },
                onLongClick = { if (kiosk) onLeaveKiosk?.invoke() else showGallery = true },
            )
    ) {
        val landscape = maxWidth > maxHeight
        // The face is bounded by BOTH sides. Sized from the width alone it grew
        // taller than a landscape screen and the eyes were cropped.
        val faceSize = minOf(maxWidth * 0.82f, maxHeight * (if (landscape) 0.5f else 0.86f))
        // The dock shows the face and nothing else. Controls are what make a
        // screen look like an app rather than an object on a shelf.
        if (!kiosk) {
            TopControls(
                languageLabel = languageLabel,
                onCycleLanguage = onCycleLanguage,
                faceColor = faceColor,
                onCycleFaceColor = onCycleFaceColor,
                motionLabel = motionLabel,
                onCycleMotion = onCycleMotion,
                transcriptLabel = transcriptLabel,
                onToggleTranscript = onToggleTranscript,
                onClose = onClose,
                onEnterKiosk = onEnterKiosk,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                // The lambda overload, because the drift changes on its own
                // clock: reading it here defers the read to layout, so a nudge
                // moves the face without recomposing everything inside it.
                .offset {
                    IntOffset(
                        x = (maxWidth * driftX).roundToPx(),
                        y = (maxHeight * driftY).roundToPx(),
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RobotFace(
                state = faceStateFor(state, micOpen = isListening),
                amplitude = amplitude,
                backgroundColor = Color.Black,
                modifier = Modifier.size(faceSize)
            )

            Spacer(Modifier.height(if (landscape) 6.dp else 12.dp))

            Text(
                text = autoModeStatusLabel(state, isListening, paused).uppercase(),
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )

            // An error is not part of the conversation and is shown whatever the
            // caption setting says: hiding "microphone permission is required"
            // would leave a screen that simply does nothing.
            val caption = captionFor(errorMessage, transcript, reply, isListening, showTranscript)
            val captionAlpha by animateFloatAsState(
                targetValue = if (caption.text.isBlank()) 0f else 1f,
                animationSpec = tween(180),
                label = "captionAlpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Landscape has no vertical room to reserve for text that is
                    // usually absent; there the box collapses to the line itself.
                    .heightIn(min = if (landscape) 26.dp else 72.dp)
                    .padding(top = if (landscape) 8.dp else 14.dp)
                    .alpha(captionAlpha),
                contentAlignment = Alignment.TopCenter,
            ) {
                TypingText(
                    text = caption.text,
                    // Speech the user is still saying arrives a word at a time
                    // already; typing it out a second time would lag behind the
                    // person's own voice.
                    animate = caption.typed && !reduceMotion,
                    color = when {
                        errorMessage != null -> colors.accentRed
                        caption.typed -> colors.textPrimary
                        else -> colors.textSecondary
                    },
                    fontSize = if (landscape) 13.sp else 15.sp,
                    maxLines = if (landscape) 1 else 3,
                )
            }
        }

        if (awaitingApproval && onApprovePlan != null && onRejectPlan != null) {
            // A proposed plan is the one moment Auto mode cannot stay voice-only:
            // the user has to say yes or no to something with real consequences,
            // so the choice is shown as two explicit buttons rather than inferred
            // from speech.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRejectPlan,
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderColor),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text("Reject", color = colors.textSecondary)
                }
                Button(
                    onClick = onApprovePlan,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreenButton),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text("Approve", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Landscape puts the meter in the corner and shrinks it. Centred and
            // full width it cut the screen in half and drew more attention than
            // the face; there is also barely any vertical room to give it.
            VoiceMeter(
                isListening = isListening,
                amplitude = amplitude,
                color = faceColor ?: colors.accentCyan,
                compact = landscape,
                reduceMotion = reduceMotion,
                modifier = if (landscape) {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 18.dp)
                        .width(118.dp)
                        .height(30.dp)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 56.dp)
                        .fillMaxWidth(0.5f)
                        .height(62.dp)
                },
            )
        }
    }
}

/**
 * What the one caption line should say, and whether it should be typed out.
 *
 * @param typed the text is the agent's, arriving all at once, so it reads
 * better revealed a character at a time. Speech being recognised is already
 * arriving piece by piece and is shown as it comes.
 */
data class AutoModeCaption(val text: String, val typed: Boolean)

fun captionFor(
    errorMessage: String?,
    transcript: String,
    reply: String,
    isListening: Boolean,
    showTranscript: Boolean,
): AutoModeCaption {
    if (errorMessage != null) return AutoModeCaption(errorMessage, typed = false)
    if (!showTranscript) return AutoModeCaption("", typed = false)
    // While the microphone is open the user's own words take the line, even if
    // an answer is still on screen: what they are saying now is the live thing.
    if (isListening && transcript.isNotBlank()) return AutoModeCaption(transcript, typed = false)
    if (isListening) return AutoModeCaption("", typed = false)
    return AutoModeCaption(reply, typed = reply.isNotBlank())
}

/**
 * Text revealed a character at a time, at a readable pace rather than a
 * dramatic one.
 *
 * The reveal is a state read in the layout phase only through the string, so a
 * long answer costs one text measure per tick and nothing else on the screen
 * recomposes. With [animate] false the whole string is drawn at once, which is
 * both the reduced-motion path and the path for text that is already streaming.
 */
@Composable
private fun TypingText(
    text: String,
    animate: Boolean,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    maxLines: Int,
) {
    var shown by remember(text, animate) { mutableIntStateOf(if (animate) 0 else text.length) }
    LaunchedEffect(text, animate) {
        if (!animate) {
            shown = text.length
            return@LaunchedEffect
        }
        // Fast enough to keep up with speech, slow enough to read as typing.
        while (shown < text.length) {
            delay(TYPING_STEP_MILLIS)
            shown = (shown + TYPING_CHARS_PER_STEP).coerceAtMost(text.length)
        }
    }

    Text(
        text = text.take(shown),
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Light,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val TYPING_STEP_MILLIS = 26L
private const val TYPING_CHARS_PER_STEP = 2

/** Language, colour and exit — kept small and dim so the face owns the screen. */
@Composable
private fun TopControls(
    languageLabel: String?,
    onCycleLanguage: (() -> Unit)?,
    faceColor: Color?,
    onCycleFaceColor: (() -> Unit)?,
    motionLabel: String?,
    onCycleMotion: (() -> Unit)?,
    transcriptLabel: String?,
    onToggleTranscript: (() -> Unit)?,
    onClose: () -> Unit,
    onEnterKiosk: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOpenDroidColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (languageLabel != null && onCycleLanguage != null) {
            Text(
                text = languageLabel.uppercase(),
                color = colors.textSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.clickable(onClick = onCycleLanguage),
            )
        }
        if (motionLabel != null && onCycleMotion != null) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = motionLabel.uppercase(),
                color = colors.textSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.clickable(onClick = onCycleMotion),
            )
        }
        if (transcriptLabel != null && onToggleTranscript != null) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = transcriptLabel.uppercase(),
                color = colors.textSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.clickable(onClick = onToggleTranscript),
            )
        }
        Spacer(Modifier.weight(1f))
        if (faceColor != null && onCycleFaceColor != null) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(faceColor)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .clickable(onClick = onCycleFaceColor)
            )
            Spacer(Modifier.width(14.dp))
        }
        if (onEnterKiosk != null) {
            IconButton(onClick = onEnterKiosk) {
                Icon(
                    imageVector = Icons.Default.Dock,
                    contentDescription = "Dock mode",
                    tint = colors.textSecondary.copy(alpha = 0.7f),
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Leave hands-free mode",
                tint = colors.textSecondary.copy(alpha = 0.7f),
            )
        }
    }
}



/**
 * The microphone indicator, in place of a button.
 *
 * One line. It bends with the voice and lies flat when nothing is being said.
 *
 * Everything else was tried here first — rounded bars, a segmented synthwave
 * equaliser, four crossing colour waves — and each of them ended up competing
 * with the face for attention, which is the one thing this screen cannot afford:
 * the face IS the interface. A single stroke says the same thing (the microphone
 * is open, it is hearing you) and then stops talking.
 */
@Composable
private fun VoiceMeter(
    isListening: Boolean,
    amplitude: Float,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    reduceMotion: Boolean = false,
) {
    val level by animateFloatAsState(
        targetValue = if (isListening) amplitude else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 110),
        label = "meterLevel",
    )
    val active by animateFloatAsState(
        targetValue = if (isListening) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 320),
        label = "meterActive",
    )
    val motion = rememberInfiniteTransition(label = "wave")
    val wavePhase by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "wavePhase",
    )

    Canvas(modifier) {
        // Reduced motion keeps the meter but drops the movement: a straight line
        // that brightens while the microphone is open still says what the meter is
        // for, and reading the phase behind this branch means the wave clock never
        // invalidates the drawing at all.
        val phase = if (reduceMotion) 0f else wavePhase
        val midY = size.height / 2f
        val steps = if (compact) 44 else 80
        val strokeWidth = if (compact) 1.4.dp.toPx() else 1.8.dp.toPx()
        val reach = size.height * 0.44f * (0.02f + level * 0.98f) * (0.05f + active * 0.95f)

        val path = Path()
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = t * size.width
            // Taper: a squared half-sine, so the line dies out at both ends instead
            // of stopping at a hard edge.
            val taper = sin(t * PI.toFloat()).let { it * it }
            // Two components, one slow and wide, one quicker and shallower. A single
            // sine reads as a test signal; two make it read as a voice.
            val wave = if (reduceMotion) {
                0f
            } else {
                sin((t * 1.6f + phase) * 2f * PI.toFloat()) * 0.72f +
                    sin((t * 3.1f - phase * 1.7f) * 2f * PI.toFloat()) * 0.28f
            }
            if (i == 0) {
                path.moveTo(x, midY + wave * reach * taper)
            } else {
                path.lineTo(x, midY + wave * reach * taper)
            }
        }

        drawPath(
            path = path,
            color = color.copy(alpha = 0.30f + active * 0.45f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}
