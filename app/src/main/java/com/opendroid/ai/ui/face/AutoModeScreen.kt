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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.ui.theme.LocalOpenDroidColors
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
                onClick = onToggleListening,
                onLongClick = { showGallery = true },
            )
    ) {
        val landscape = maxWidth > maxHeight
        // The face is bounded by BOTH sides. Sized from the width alone it grew
        // taller than a landscape screen and the eyes were cropped.
        val faceSize = minOf(maxWidth * 0.82f, maxHeight * (if (landscape) 0.5f else 0.86f))
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
            RobotFace(
                state = faceStateFor(state, micOpen = isListening),
                amplitude = amplitude,
                backgroundColor = Color.Black,
                modifier = Modifier.size(faceSize)
            )

            Spacer(Modifier.height(if (landscape) 6.dp else 12.dp))

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
                    // Landscape has no vertical room to reserve for text that is
                    // usually absent; there the box collapses to the line itself.
                    .heightIn(min = if (landscape) 30.dp else 84.dp)
                    .padding(top = if (landscape) 8.dp else 14.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = subtitle,
                    color = if (errorMessage != null) colors.accentRed else colors.textPrimary,
                    fontSize = if (landscape) 15.sp else 20.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    maxLines = if (landscape) 1 else 3,
                    overflow = TextOverflow.Ellipsis,
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
            // Landscape puts the meter in the corner and shrinks it. Centred and
            // full width it cut the screen in half and drew more attention than
            // the face; there is also barely any vertical room to give it.
            VoiceMeter(
                isListening = isListening,
                amplitude = amplitude,
                color = faceColor ?: colors.accentCyan,
                compact = landscape,
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
 * Bars that rise with the voice, with a wave travelling through them so the
 * meter looks alive between syllables instead of freezing at a level. It shows
 * what a mic button showed — whether the microphone is open — without claiming
 * the bottom of the screen as a control, and it proves the microphone is
 * actually hearing something, which a static icon never did.
 *
 * Idle it settles into a row of dots: still visible, no longer asking for
 * attention.
 */
@Composable
private fun VoiceMeter(
    isListening: Boolean,
    amplitude: Float,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
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
    val wave = rememberInfiniteTransition(label = "meterWave")
    val phase by wave.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "meterPhase",
    )

    val bars = if (compact) 7 else 13
    // Peaks fall on their own, the way a hardware meter's hold marker does. Kept
    // outside the draw lambda so the fall is continuous rather than restarting
    // with every recomposition.
    val peaks = remember(bars) { FloatArray(bars) }

    Canvas(modifier) {
        // Synthwave: square segments stacked into columns, a horizon line, and a
        // reflection under it. No rounded caps anywhere - the hard edges are the
        // whole look.
        val horizonY = size.height * 0.74f
        val columnH = horizonY
        val gap = size.width / (bars * 3f)
        val barW = (size.width - gap * (bars - 1)) / bars
        val segH = max(2.dp.toPx(), columnH / 6f)
        val segGap = segH * 0.45f
        val step = segH + segGap

        repeat(bars) { i ->
            // Envelope: the middle of the row reacts most, so the shape reads as a
            // voice rather than as a progress bar.
            val fromCentre = kotlin.math.abs(i - (bars - 1) / 2f) / ((bars - 1) / 2f)
            val envelope = 0.35f + 0.65f * (1f - fromCentre * fromCentre)
            // Travelling wave: each bar lags the one before it.
            val ripple = 0.55f + 0.45f * sin((phase - i * 0.09f) * 2f * PI.toFloat())
            val reach = (level * envelope * ripple).coerceIn(0f, 1f)

            val h = max(segH, columnH * (0.04f + reach * 0.96f) * (0.22f + active * 0.78f))
            peaks[i] = max(peaks[i] * 0.93f, h)

            val x = i * (barW + gap)
            val lit = (h / step).toInt().coerceAtLeast(0)

            // Glow: one faint wide copy of the column. A DrawScope cannot blur on
            // every supported API level, and a second rectangle costs nothing.
            drawRect(
                color = color.copy(alpha = (0.05f + active * 0.10f) * (0.4f + reach)),
                topLeft = Offset(x - barW * 0.4f, horizonY - h - segH),
                size = Size(barW * 1.8f, h + segH * 2f),
            )

            repeat(lit + 1) { s ->
                val segTop = horizonY - (s + 1) * step + segGap
                if (segTop < horizonY - h - step) return@repeat
                // Bottom of the column keeps the face colour, the top burns towards
                // magenta - the gradient is what makes it read as synthwave rather
                // than as a plain equaliser.
                val t = ((s + 1) * step / columnH).coerceIn(0f, 1f)
                val segColor = lerp(color, Color(0xFFFF2FB0), t)

                drawRect(
                    color = segColor.copy(alpha = 0.35f + active * 0.6f),
                    topLeft = Offset(x, segTop),
                    size = Size(barW, segH),
                )
                // Reflection below the horizon: shorter, dimmer, and it fades out
                // with distance the way a wet-floor reflection does.
                val mirrorTop = horizonY + (horizonY - segTop - segH) * 0.42f
                if (mirrorTop < size.height) {
                    drawRect(
                        color = segColor.copy(alpha = (0.18f + active * 0.22f) * (1f - t * 0.7f)),
                        topLeft = Offset(x, mirrorTop),
                        size = Size(barW, segH * 0.6f),
                    )
                }
            }

            // Peak hold: a single bright segment riding the top of the column.
            val peakTop = horizonY - peaks[i]
            drawRect(
                color = Color.White.copy(alpha = 0.25f + active * 0.5f),
                topLeft = Offset(x, peakTop),
                size = Size(barW, max(1f, segH * 0.35f)),
            )
        }

        // The horizon itself.
        drawRect(
            color = color.copy(alpha = 0.25f + active * 0.35f),
            topLeft = Offset(0f, horizonY),
            size = Size(size.width, max(1f, size.height * 0.012f)),
        )
    }
}
