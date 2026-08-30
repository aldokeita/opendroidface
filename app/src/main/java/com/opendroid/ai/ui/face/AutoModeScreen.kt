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

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.ui.theme.LocalOpenDroidColors
import kotlin.math.max

/**
 * One short line telling the user what is happening. In Auto mode this is the
 * only text on screen, so it has to carry the whole status by itself.
 *
 * [isListening] is the microphone's own state, which leads the agent's: the mic
 * is already open while the agent is still Idle.
 */
fun autoModeStatusLabel(state: AgentState, isListening: Boolean): String = when {
    isListening -> "Listening…"
    state is AgentState.Thinking -> "Thinking…"
    state is AgentState.PlanProposed -> "Waiting for your approval"
    state is AgentState.ExecutingPlan -> state.currentStepDesc.ifBlank { "Working on it…" }
    state is AgentState.Speaking -> "Speaking…"
    state is AgentState.Error -> "Something went wrong"
    state is AgentState.Listening -> "Listening…"
    else -> "Tap anywhere to speak"
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
    amplitude: Float = 0f,
    onApprovePlan: (() -> Unit)? = null,
    onRejectPlan: (() -> Unit)? = null,
    languageLabel: String? = null,
    onCycleLanguage: (() -> Unit)? = null,
    faceColor: Color? = null,
    onCycleFaceColor: (() -> Unit)? = null,
) {
    val colors = LocalOpenDroidColors.current
    val awaitingApproval = state is AgentState.PlanProposed
    var showGallery by remember { mutableStateOf(false) }

    if (showGallery) {
        FaceGallery(onClose = { showGallery = false }, modifier = modifier)
        return
    }

    Box(
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
                onClick = onToggleListening,
                onLongClick = { showGallery = true },
            )
    ) {
        TopControls(
            languageLabel = languageLabel,
            onCycleLanguage = onCycleLanguage,
            faceColor = faceColor,
            onCycleFaceColor = onCycleFaceColor,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                RobotFace(
                    state = faceStateFor(state, micOpen = isListening),
                    amplitude = amplitude,
                    backgroundColor = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Shorter than it is wide: a square box leaves a band of
                        // empty screen under the eyes and pushes the status line
                        // away from the face it belongs to.
                        .height(maxWidth * 0.82f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = autoModeStatusLabel(state, isListening).uppercase(),
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )

            // The transcript is the loud line: it is what the user is actually
            // reading back. Its box keeps a fixed minimum so the face does not
            // jump every time a partial result arrives.
            val subtitle = errorMessage ?: transcript
            val subtitleAlpha by animateFloatAsState(
                targetValue = if (subtitle.isBlank()) 0f else 1f,
                animationSpec = tween(180),
                label = "subtitleAlpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp)
                    .padding(top = 14.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = subtitle,
                    color = if (errorMessage != null) colors.accentRed else colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(subtitleAlpha),
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
            VoiceMeter(
                isListening = isListening,
                amplitude = amplitude,
                color = faceColor ?: colors.accentCyan,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
                    .fillMaxWidth(0.5f)
                    .height(36.dp),
            )
        }
    }
}

/** Language, colour and exit — kept small and dim so the face owns the screen. */
@Composable
private fun TopControls(
    languageLabel: String?,
    onCycleLanguage: (() -> Unit)?,
    faceColor: Color?,
    onCycleFaceColor: (() -> Unit)?,
    onClose: () -> Unit,
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
 * Seven bars that rise with the voice. It shows the same thing a mic button did —
 * whether the microphone is open — without claiming the bottom of the screen as
 * a control, and it doubles as proof the microphone is actually hearing
 * something, which a static icon never gave.
 */
@Composable
private fun VoiceMeter(
    isListening: Boolean,
    amplitude: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val level by animateFloatAsState(
        targetValue = if (isListening) amplitude else 0f,
        animationSpec = tween(90),
        label = "meterLevel",
    )
    val active by animateFloatAsState(
        targetValue = if (isListening) 1f else 0f,
        animationSpec = tween(280),
        label = "meterActive",
    )

    Canvas(modifier) {
        val bars = 7
        val gap = size.width / (bars * 2.2f)
        val barW = (size.width - gap * (bars - 1)) / bars
        // Middle bars react most, so the meter reads as a voice rather than a
        // level bar. Idle leaves a row of dots, which is quieter than an icon.
        val shape = listOf(0.35f, 0.6f, 0.85f, 1f, 0.85f, 0.6f, 0.35f)

        repeat(bars) { i ->
            val h = max(
                barW * 0.55f,
                size.height * (0.10f + level * shape[i] * 0.9f) * (0.35f + active * 0.65f),
            )
            val x = i * (barW + gap)
            drawRoundRect(
                color = color.copy(alpha = 0.35f + active * 0.55f),
                topLeft = Offset(x, size.height / 2f - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}
