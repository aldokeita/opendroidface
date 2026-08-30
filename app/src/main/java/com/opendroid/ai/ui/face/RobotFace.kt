// Robot face, drawn with Canvas — deliberately NOT Lottie in phase 1.
//
// The face parameters have to be driven continuously by voice amplitude
// (phase 2-3), and that is far easier with geometry we control than with a
// Lottie animation progress. `lottie-compose` is already on the classpath and
// stays reserved for the "custom face" backlog item: loading a user-authored
// character from an external file.
//
// Usage in ui/screens/ChatScreen.kt:
//
//     val agentState by viewModel.visibleAgentState.collectAsState()
//     RobotFace(
//         state = agentState,
//         modifier = Modifier.fillMaxWidth().height(160.dp)
//     )
//
// The composable draws into whatever box it is given and derives every
// dimension from `size.minDimension`, so the same code renders as a small chat
// header and as a full-screen Auto mode face.

package com.opendroid.ai.ui.face

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.ui.theme.LocalOpenDroidColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private const val TRANSITION_MS = 320

/** One full turn of every ambient decoration (rings, dots, progress sweep). */
private const val AMBIENT_CYCLE_MS = 2400

@Composable
fun RobotFace(
    state: AgentState,
    modifier: Modifier = Modifier,
    /** Voice amplitude 0f..1f. Phases 2-3 feed this from VoiceAmplitude.level. */
    amplitude: Float = 0f,
) {
    val colors = LocalOpenDroidColors.current
    val expression = state.toExpression()
    val target = expression.params()

    // Each parameter animates on its own so a change of expression reads as the
    // face moving, not as one drawing being swapped for another.
    val eyeOpen by animateFloatAsState(target.eyeOpen, tween(TRANSITION_MS), label = "eyeOpen")
    val eyeSquint by animateFloatAsState(target.eyeSquint, tween(TRANSITION_MS), label = "eyeSquint")
    val browAngle by animateFloatAsState(target.browAngle, tween(TRANSITION_MS), label = "browAngle")
    val browRaise by animateFloatAsState(target.browRaise, tween(TRANSITION_MS), label = "browRaise")
    val mouthCurve by animateFloatAsState(target.mouthCurve, tween(TRANSITION_MS), label = "mouthCurve")
    val headTilt by animateFloatAsState(target.headTilt, tween(TRANSITION_MS), label = "headTilt")
    val pupilX by animateFloatAsState(target.pupilOffsetX, tween(TRANSITION_MS), label = "pupilX")
    val pupilY by animateFloatAsState(target.pupilOffsetY, tween(TRANSITION_MS), label = "pupilY")

    // Mouth: follows amplitude while speaking/listening, otherwise the static target.
    val liveMouth = when (state) {
        is AgentState.Speaking -> amplitude
        is AgentState.Listening -> amplitude * 0.35f
        else -> target.mouthOpen
    }
    val mouthOpen by animateFloatAsState(liveMouth, tween(60, easing = LinearEasing), label = "mouthOpen")

    // Decorations fade in and out instead of appearing abruptly, so a state change
    // never pops a ring or a row of dots into existence mid-frame.
    val ringAmount by animateFloatAsState(
        if (expression == FaceExpression.LISTENING) 1f else 0f,
        tween(TRANSITION_MS), label = "ringAmount"
    )
    val dotsAmount by animateFloatAsState(
        if (expression == FaceExpression.THINKING) 1f else 0f,
        tween(TRANSITION_MS), label = "dotsAmount"
    )
    val progressAmount by animateFloatAsState(
        if (expression == FaceExpression.FOCUSED) 1f else 0f,
        tween(TRANSITION_MS), label = "progressAmount"
    )
    // Idle drift is the only motion in the resting state; it stops as soon as the
    // agent is doing something, otherwise it fights with the real animations.
    val driftAmount by animateFloatAsState(
        if (expression == FaceExpression.NEUTRAL) 1f else 0f,
        tween(TRANSITION_MS), label = "driftAmount"
    )

    // Random blink. Kept out of the ambient transition because the whole point is
    // that its timing is irregular.
    var blink by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(3000, 6500))
            blink = 0f
            delay(90)
            blink = 1f
        }
    }
    val blinkAnim by animateFloatAsState(blink, tween(90), label = "blink")

    // A single ambient clock drives every repeating decoration. One infinite
    // transition is cheaper than four, and it keeps the decorations in phase.
    val ambient = rememberInfiniteTransition(label = "ambient")
    val phase by ambient.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(AMBIENT_CYCLE_MS, easing = LinearEasing)),
        label = "phase",
    )

    val accent = when (target.accent) {
        FaceAccent.PRIMARY -> colors.accentNeonGreen
        FaceAccent.CYAN -> colors.accentCyan
        FaceAccent.PURPLE -> colors.accentPurple
        FaceAccent.ORANGE -> colors.accentOrange
        FaceAccent.RED -> colors.accentRed
    }

    val description = expression.contentDescription()

    Box(modifier.semantics { contentDescription = description }) {
        // Every animated value below is read INSIDE the draw lambda on purpose:
        // a state read in the draw phase invalidates only the drawing, so the
        // ambient clock never triggers recomposition at 60fps.
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension / 10f
            val drift = sin(phase * 2f * PI.toFloat()) * unit * 0.12f * driftAmount

            drawDecorations(
                accent = accent,
                unit = unit,
                phase = phase,
                ringAmount = ringAmount,
                dotsAmount = dotsAmount,
                progressAmount = progressAmount,
                amplitude = amplitude,
            )

            translate(top = drift) {
                rotate(degrees = headTilt, pivot = center) {
                    drawFace(
                        accent = accent,
                        dim = colors.textSecondary,
                        unit = unit,
                        eyeOpen = eyeOpen * blinkAnim,
                        eyeSquint = eyeSquint,
                        browAngle = browAngle,
                        browRaise = browRaise,
                        mouthOpen = mouthOpen,
                        mouthCurve = mouthCurve,
                        pupilX = pupilX,
                        pupilY = pupilY,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawFace(
    accent: Color,
    dim: Color,
    unit: Float,
    eyeOpen: Float,
    eyeSquint: Float,
    browAngle: Float,
    browRaise: Float,
    mouthOpen: Float,
    mouthCurve: Float,
    pupilX: Float,
    pupilY: Float,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    val eyeDx = unit * 2f
    val eyeRadius = unit * 1.1f
    val eyeHeight = max(unit * 0.12f, eyeRadius * eyeOpen * (1f - eyeSquint * 0.55f))

    // ── Eyes (rounded capsules; height follows eyeOpen) ──
    listOf(-eyeDx, eyeDx).forEach { dx ->
        drawOval(
            color = accent,
            topLeft = Offset(cx + dx - eyeRadius, cy - eyeHeight),
            size = Size(eyeRadius * 2f, eyeHeight * 2f),
        )
        // The pupil only reads as a pupil while the eye is open enough to hold it.
        if (eyeOpen > 0.35f) {
            drawCircle(
                color = dim,
                radius = eyeRadius * 0.34f,
                center = Offset(
                    x = cx + dx + pupilX * eyeRadius * 0.4f,
                    y = cy + pupilY * eyeHeight * 0.4f,
                ),
            )
        }
    }

    // ── Brows ──
    val browY = cy - eyeRadius * 1.9f - browRaise * unit * 0.5f
    val browHalf = unit * 1.1f
    listOf(-eyeDx to 1f, eyeDx to -1f).forEach { (dx, dir) ->
        val tilt = Math.toRadians((browAngle * dir).toDouble())
        val dy = (browHalf * kotlin.math.sin(tilt)).toFloat()
        drawLine(
            color = accent,
            start = Offset(cx + dx - browHalf, browY - dy),
            end = Offset(cx + dx + browHalf, browY + dy),
            strokeWidth = unit * 0.22f,
        )
    }

    // ── Mouth ──
    // Fixed width; height follows mouthOpen, vertical position follows mouthCurve.
    val mouthW = unit * 2.4f
    val mouthH = max(unit * 0.18f, unit * 1.3f * mouthOpen)
    val mouthY = cy + unit * 2.4f + mouthCurve * unit * -0.3f
    drawOval(
        color = accent,
        topLeft = Offset(cx - mouthW / 2f, mouthY - mouthH / 2f),
        size = Size(mouthW, mouthH),
    )
}

/**
 * Rings, dots and the progress sweep. These sit behind the face and are drawn
 * before it so the face itself is never occluded.
 *
 * [phase] runs 0f..1f once per ambient cycle; each decoration derives its own
 * timing from it rather than owning a separate animation.
 */
private fun DrawScope.drawDecorations(
    accent: Color,
    unit: Float,
    phase: Float,
    ringAmount: Float,
    dotsAmount: Float,
    progressAmount: Float,
    amplitude: Float,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    // ── Listening: rings expanding outward from the face ──
    if (ringAmount > 0.01f) {
        val baseRadius = unit * 3.2f
        // Two rings half a cycle apart read as a continuous emission rather than
        // a single pulse that restarts.
        listOf(0f, 0.5f).forEach { offset ->
            val p = (phase + offset) % 1f
            val radius = baseRadius + p * unit * (2.2f + amplitude * 1.8f)
            drawCircle(
                color = accent.copy(alpha = (1f - p) * 0.35f * ringAmount),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = unit * 0.09f),
            )
        }
    }

    // ── Thinking: three dots below the face, lighting up in turn ──
    if (dotsAmount > 0.01f) {
        val dotY = cy + unit * 3.9f
        val spacing = unit * 0.75f
        repeat(3) { i ->
            // Each dot peaks a third of a cycle after the previous one.
            val p = (phase - i * 0.33f + 1f) % 1f
            val glow = (sin(p * PI.toFloat()) * 0.9f + 0.1f).coerceIn(0f, 1f)
            drawCircle(
                color = accent.copy(alpha = glow * dotsAmount),
                radius = unit * 0.17f,
                center = Offset(cx + (i - 1) * spacing, dotY),
            )
        }
    }

    // ── Executing: a thin progress bar sweeping under the face ──
    if (progressAmount > 0.01f) {
        val barY = cy + unit * 4.0f
        val halfW = unit * 2.6f
        val thickness = unit * 0.08f
        drawLine(
            color = accent.copy(alpha = 0.18f * progressAmount),
            start = Offset(cx - halfW, barY),
            end = Offset(cx + halfW, barY),
            strokeWidth = thickness,
        )
        // Indeterminate: the head slides across and wraps, since the agent gives
        // no percentage — only "still working".
        val headLen = halfW * 0.5f
        val headStart = -halfW + phase * (halfW * 2f + headLen) - headLen
        drawLine(
            color = accent.copy(alpha = 0.9f * progressAmount),
            start = Offset(cx + headStart.coerceAtLeast(-halfW), barY),
            end = Offset(cx + (headStart + headLen).coerceAtMost(halfW), barY),
            strokeWidth = thickness,
        )
    }
}
