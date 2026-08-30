// Robot face, drawn with Canvas — deliberately NOT Lottie.
//
// The face parameters have to be driven continuously by voice amplitude
// (phases 2-3), and a pre-rendered animation cannot do that: Lottie exposes a
// progress value, not a mouth. Every shape here is geometry we control, so the
// same code serves a static expression, a mouth following a waveform, and any
// blend between the two. `lottie-compose` stays reserved for the "custom face"
// backlog item, where the user supplies their own character.
//
// The look follows the EMO/Cozmo family of robot faces: a dark panel, two large
// glossy eyes carrying almost all of the expression, eyelids that tilt for mood,
// and a small mouth. Expression comes from SHAPE, not colour — the face keeps
// the colour the user chose so it reads as one character, and only an error
// turns it red.
//
// Usage in ui/screens/ChatScreen.kt:
//
//     val agentState by viewModel.visibleAgentState.collectAsState()
//     RobotFace(
//         state = agentState,
//         modifier = Modifier.fillMaxWidth().height(160.dp)
//     )
//
// Every dimension derives from the smaller side of the box it is given, so the
// same composable renders as a chat header and as a full-screen hands-free face.

package com.opendroid.ai.ui.face

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
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

/** Below this, an amplitude reading is treated as "nothing is publishing". */
private const val AMPLITUDE_FLOOR = 0.03f

@Composable
fun RobotFace(
    state: AgentState,
    modifier: Modifier = Modifier,
    /** Voice amplitude 0f..1f. Phases 2-3 feed this from VoiceAmplitude.level. */
    amplitude: Float = 0f,
    /** Overrides the user's stored colour; used by previews and tests. */
    faceColor: Color? = null,
) {
    val colors = LocalOpenDroidColors.current
    val expression = state.toExpression()
    val target = expression.params()

    val storedColorId by rememberFaceColorStore().colorId.collectAsState()
    val chosen = faceColor ?: faceColorFor(storedColorId).color
    // Colour is otherwise constant on purpose; an error is the one state whose
    // meaning the shape alone cannot carry.
    val liveColor = if (target.accent == FaceAccent.RED) colors.accentRed else chosen
    val eyeColor by animateColorAsState(liveColor, tween(TRANSITION_MS), label = "eyeColor")

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
    // Nothing publishes amplitude while the assistant talks on the local TTS path
    // before a word boundary arrives, so a speaking face falls back to the ambient
    // clock rather than sitting with its mouth shut.
    val talkFallback = if (state is AgentState.Speaking && amplitude < AMPLITUDE_FLOOR) 1f else 0f
    val talkAmount by animateFloatAsState(talkFallback, tween(TRANSITION_MS), label = "talkAmount")

    // Random blink. Kept out of the ambient transition because the whole point is
    // that its timing is irregular.
    var blink by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(3000, 6500))
            blink = 0f
            delay(110)
            blink = 1f
        }
    }
    val blinkAnim by animateFloatAsState(blink, tween(110), label = "blink")

    // A single ambient clock drives every repeating decoration. One infinite
    // transition is cheaper than four, and it keeps the decorations in phase.
    val ambient = rememberInfiniteTransition(label = "ambient")
    val phase by ambient.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(AMBIENT_CYCLE_MS, easing = LinearEasing)),
        label = "phase",
    )

    val description = expression.contentDescription()
    val panelColor = if (colors.isDark) {
        // A shade below the app background so the panel reads as a screen inside
        // the screen rather than a floating rectangle.
        lerp(colors.background, Color.Black, 0.55f)
    } else {
        Color(0xFF10151C)
    }

    Box(modifier.semantics { contentDescription = description }) {
        // Every animated value below is read INSIDE the draw lambda on purpose:
        // a state read in the draw phase invalidates only the drawing, so the
        // ambient clock never triggers recomposition at 60fps.
        Canvas(Modifier.fillMaxSize()) {
            val face = size.minDimension
            val unit = face / 10f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val drift = sin(phase * 2f * PI.toFloat()) * unit * 0.10f * driftAmount
            // Three mouth movements per ambient cycle: slow enough not to look like
            // a rattle, fast enough to read as speech.
            val talk = talkAmount * (0.30f + 0.30f * sin(phase * 2f * PI.toFloat() * 3f))

            drawPanel(panelColor, face, Offset(cx, cy))

            drawDecorations(
                accent = eyeColor,
                unit = unit,
                center = Offset(cx, cy),
                phase = phase,
                ringAmount = ringAmount,
                dotsAmount = dotsAmount,
                progressAmount = progressAmount,
                amplitude = amplitude,
            )

            translate(top = drift) {
                rotate(degrees = headTilt, pivot = center) {
                    drawFace(
                        eyeColor = eyeColor,
                        panelColor = panelColor,
                        unit = unit,
                        center = Offset(cx, cy),
                        eyeOpen = eyeOpen * blinkAnim,
                        eyeSquint = eyeSquint,
                        lidAngle = browAngle,
                        browRaise = browRaise,
                        mouthOpen = max(mouthOpen, talk),
                        mouthCurve = mouthCurve,
                        gazeX = pupilX,
                        gazeY = pupilY,
                    )
                }
            }
        }
    }
}

/** The dark screen the face lives on, with a soft vignette so it has depth. */
private fun DrawScope.drawPanel(panelColor: Color, face: Float, center: Offset) {
    val side = face * 0.98f
    val topLeft = Offset(center.x - side / 2f, center.y - side / 2f)
    val radius = CornerRadius(side * 0.24f, side * 0.24f)

    drawRoundRect(color = panelColor, topLeft = topLeft, size = Size(side, side), cornerRadius = radius)
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
            center = center,
            radius = side * 0.75f,
        ),
        topLeft = topLeft,
        size = Size(side, side),
        cornerRadius = radius,
    )
}

private fun DrawScope.drawFace(
    eyeColor: Color,
    panelColor: Color,
    unit: Float,
    center: Offset,
    eyeOpen: Float,
    eyeSquint: Float,
    lidAngle: Float,
    browRaise: Float,
    mouthOpen: Float,
    mouthCurve: Float,
    gazeX: Float,
    gazeY: Float,
) {
    val cx = center.x
    val cy = center.y - unit * 0.35f

    val eyeW = unit * 1.85f * (1f + browRaise * 0.06f)
    val eyeHFull = unit * 2.05f * (1f + browRaise * 0.08f)
    // A closed eye is a line, not a disappearance: keeping a sliver means a blink
    // reads as a blink instead of the eyes vanishing for two frames.
    val eyeH = max(unit * 0.16f, eyeHFull * eyeOpen)
    // Wide enough apart that the bloom of one never touches the other; adjacent
    // eyes read as a single bar rather than a face.
    val eyeDx = unit * 1.75f

    listOf(-1f, 1f).forEach { side ->
        val eyeCenter = Offset(cx + side * eyeDx + gazeX * unit * 0.18f, cy + gazeY * unit * 0.14f)
        drawEye(
            center = eyeCenter,
            width = eyeW,
            height = eyeH,
            color = eyeColor,
            unit = unit,
            gazeX = gazeX,
            gazeY = gazeY,
        )
        drawLid(
            center = eyeCenter,
            width = eyeW,
            height = eyeH,
            panelColor = panelColor,
            // Mirrored so both lids slope inward or outward together; a single
            // direction would make the face look like it is tilting, not feeling.
            angle = lidAngle * side * -1f,
            drop = eyeSquint,
        )
    }

    drawMouth(
        center = Offset(cx, cy + unit * 2.55f),
        unit = unit,
        color = eyeColor,
        open = mouthOpen,
        curve = mouthCurve,
    )
}

/**
 * One glossy eye: a rounded rect filled with a radial gradient, a bloom behind it
 * and two specular highlights. The highlights are what make it read as a lens
 * rather than a coloured box, so they move with the gaze.
 */
private fun DrawScope.drawEye(
    center: Offset,
    width: Float,
    height: Float,
    color: Color,
    unit: Float,
    gazeX: Float,
    gazeY: Float,
) {
    val corner = CornerRadius(width * 0.42f, width * 0.42f)
    val topLeft = Offset(center.x - width / 2f, center.y - height / 2f)

    // Bloom: many faint concentric rounded rects instead of a blur, which Compose
    // cannot do inside a DrawScope on every supported API level. The count matters
    // more than the opacity - too few steps and the falloff shows up as visible
    // bands around the eye.
    repeat(7) { i ->
        val grow = unit * (0.06f + i * 0.11f)
        drawRoundRect(
            color = color.copy(alpha = 0.045f - i * 0.006f),
            topLeft = Offset(topLeft.x - grow, topLeft.y - grow),
            size = Size(width + grow * 2f, height + grow * 2f),
            cornerRadius = CornerRadius(corner.x + grow, corner.y + grow),
        )
    }

    val bright = lerp(color, Color.White, 0.35f)
    val deep = lerp(color, Color(0xFF03121F), 0.72f)
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(bright, color, deep),
            center = Offset(center.x - width * 0.16f, center.y - height * 0.22f),
            radius = max(width, height) * 0.95f,
        ),
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = corner,
    )

    if (height > unit * 0.5f) {
        // Big highlight up and to the left, small one opposite: the pairing is what
        // sells a curved, wet-looking surface.
        drawCircle(
            color = Color.White.copy(alpha = 0.92f),
            radius = width * 0.19f,
            center = Offset(
                center.x - width * 0.20f + gazeX * width * 0.10f,
                center.y - height * 0.24f + gazeY * height * 0.10f,
            ),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = width * 0.09f,
            center = Offset(
                center.x + width * 0.21f + gazeX * width * 0.08f,
                center.y + height * 0.22f + gazeY * height * 0.08f,
            ),
        )
    }
}

/**
 * The upper eyelid, drawn in the panel colour so it cuts into the eye.
 *
 * This is where nearly all of the mood lives: angle it down toward the nose for
 * anger or focus, up for sadness, drop it for tiredness.
 */
private fun DrawScope.drawLid(
    center: Offset,
    width: Float,
    height: Float,
    panelColor: Color,
    angle: Float,
    drop: Float,
) {
    if (drop <= 0.01f && angle == 0f) return
    val coverage = height * (0.14f + drop * 0.5f)
    // Oversized so the rotated corners never expose the eye behind them.
    val lidW = width * 2f
    val lidH = height * 1.4f

    rotate(degrees = angle, pivot = center) {
        drawRoundRect(
            color = panelColor,
            topLeft = Offset(center.x - lidW / 2f, center.y - height / 2f - lidH + coverage),
            size = Size(lidW, lidH),
            cornerRadius = CornerRadius(width * 0.10f, width * 0.10f),
        )
    }
}

/**
 * The mouth. Closed it is a curved stroke whose bend carries the mood; open it
 * becomes a filled shape driven by the voice.
 */
private fun DrawScope.drawMouth(
    center: Offset,
    unit: Float,
    color: Color,
    open: Float,
    curve: Float,
) {
    val width = unit * 1.75f
    if (open > 0.12f) {
        val height = unit * 1.6f * open
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
            size = Size(width, height),
            cornerRadius = CornerRadius(width * 0.45f, width * 0.45f),
        )
        return
    }

    val bend = curve * unit * 0.75f
    val path = Path().apply {
        moveTo(center.x - width / 2f, center.y - bend * 0.35f)
        quadraticTo(center.x, center.y + bend, center.x + width / 2f, center.y - bend * 0.35f)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = unit * 0.22f, cap = StrokeCap.Round),
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
    center: Offset,
    phase: Float,
    ringAmount: Float,
    dotsAmount: Float,
    progressAmount: Float,
    amplitude: Float,
) {
    val cx = center.x
    val cy = center.y

    // ── Listening: rings expanding outward from the face ──
    if (ringAmount > 0.01f) {
        val baseRadius = unit * 4.4f
        // Two rings half a cycle apart read as a continuous emission rather than
        // a single pulse that restarts.
        listOf(0f, 0.5f).forEach { offset ->
            val p = (phase + offset) % 1f
            val radius = baseRadius + p * unit * (2.0f + amplitude * 2.2f)
            drawCircle(
                color = accent.copy(alpha = (1f - p) * 0.30f * ringAmount),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = unit * 0.08f),
            )
        }
    }

    // ── Thinking: three dots below the face, lighting up in turn ──
    if (dotsAmount > 0.01f) {
        val dotY = cy + unit * 5.4f
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
        val barY = cy + unit * 5.4f
        val halfW = unit * 2.6f
        val thickness = unit * 0.08f
        drawLine(
            color = accent.copy(alpha = 0.18f * progressAmount),
            start = Offset(cx - halfW, barY),
            end = Offset(cx + halfW, barY),
            strokeWidth = thickness,
            cap = StrokeCap.Round,
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
            cap = StrokeCap.Round,
        )
    }
}
