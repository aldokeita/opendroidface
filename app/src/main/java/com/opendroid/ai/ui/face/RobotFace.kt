// Robot face, drawn with Canvas — deliberately NOT Lottie.
//
// The face parameters have to be driven continuously by voice amplitude
// (phases 2-3), and a pre-rendered animation cannot do that: Lottie exposes a
// progress value, not a mouth. Every shape here is geometry we control, so the
// same code serves a static expression, a mouth following a waveform, and any
// blend between the two. `lottie-compose` stays reserved for the "custom face"
// backlog item, where the user supplies their own character.
//
// The look follows screen-faced robots (EMO, Cozmo, and the flat vector robots
// that use kaomoji eyes): a dark panel, two large eyes that CHANGE SHAPE per
// emotion, a small mouth, and a support icon in the corner for what a face
// cannot say by itself.
//
// Expression comes from shape, not colour — the face keeps the colour the user
// chose so it reads as one character, and only an error turns it red.
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
    /** Overrides the expression derived from [state]; used by the gallery and by phase 4. */
    expressionOverride: FaceExpression? = null,
    /** Overrides the user's stored colour; used by previews and the gallery. */
    faceColor: Color? = null,
    /** Overrides the user's stored style; used by the gallery to show both at once. */
    styleOverride: FaceStyle? = null,
    /** The surface the face is drawn on. Defaults to the app background. */
    backgroundColor: Color? = null,
) {
    val colors = LocalOpenDroidColors.current

    // Emotion declared by the model, or guessed from the words it is speaking when
    // it declared nothing. It only ever modulates: faceExpressionFor keeps the
    // agent's own state whenever that state is something the user needs to see.
    val mood = rememberFaceMood()
    val declared by mood.emotion.collectAsState()
    LaunchedEffect(state) {
        if (state is AgentState.Speaking) {
            // Guessed from the reply and then REMEMBERED. Speaking lasts as long as
            // the sentence does; without publishing it, the reaction vanished with
            // the last syllable and the face was blank by the time the user looked
            // up. FaceMood.publish ignores null, so a plain answer changes nothing.
            mood.publish(inferEmotionFromReply(state.text))
        } else {
            mood.expireIfStale()
        }
    }
    val emotion = declared ?: (state as? AgentState.Speaking)?.let { inferEmotionFromReply(it.text) }

    val expression = expressionOverride ?: faceExpressionFor(state, emotion)
    val target = expression.params()

    val storedColorId by rememberFaceColorStore().colorId.collectAsState()
    val storedStyle by rememberFaceStyleStore().style.collectAsState()
    val faceStyle = styleOverride ?: storedStyle
    // Colour is constant across states on purpose: the face is a character, not a
    // status light. What it is doing is said by shape and by the corner icon.
    val chosen = faceColor ?: faceColorFor(storedColorId).color
    val eyeColor by animateColorAsState(chosen, tween(TRANSITION_MS), label = "eyeColor")

    // Each parameter animates on its own so a change of expression reads as the
    // face moving, not as one drawing being swapped for another.
    val eyeOpen by animateFloatAsState(target.eyeOpen, tween(TRANSITION_MS), label = "eyeOpen")
    val eyeSquint by animateFloatAsState(target.eyeSquint, tween(TRANSITION_MS), label = "eyeSquint")
    val eyeScale by animateFloatAsState(target.eyeScale, tween(TRANSITION_MS), label = "eyeScale")
    val lidAngle by animateFloatAsState(target.lidAngle, tween(TRANSITION_MS), label = "lidAngle")
    val mouthCurve by animateFloatAsState(target.mouthCurve, tween(TRANSITION_MS), label = "mouthCurve")
    val headTilt by animateFloatAsState(target.headTilt, tween(TRANSITION_MS), label = "headTilt")
    val gazeX by animateFloatAsState(target.gazeX, tween(TRANSITION_MS), label = "gazeX")
    val gazeY by animateFloatAsState(target.gazeY, tween(TRANSITION_MS), label = "gazeY")

    // Mouth: follows amplitude while speaking/listening, otherwise the static target.
    val liveMouth = when {
        expressionOverride != null -> target.mouthOpen
        state is AgentState.Speaking -> amplitude
        state is AgentState.Listening -> amplitude * 0.35f
        else -> target.mouthOpen
    }
    val mouthOpen by animateFloatAsState(liveMouth, tween(60, easing = LinearEasing), label = "mouthOpen")

    // Decorations fade in and out instead of appearing abruptly, so a state change
    // never pops a ring into existence mid-frame.
    val ringAmount by animateFloatAsState(
        if (expression == FaceExpression.LISTENING) 1f else 0f,
        tween(TRANSITION_MS), label = "ringAmount"
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
    val talkFallback =
        if (expressionOverride == null && state is AgentState.Speaking && amplitude < AMPLITUDE_FLOOR) 1f else 0f
    val talkAmount by animateFloatAsState(talkFallback, tween(TRANSITION_MS), label = "talkAmount")

    // Random blink. Kept out of the ambient transition because the whole point is
    // that its timing is irregular. Shape-changed eyes (an arc, a heart) are not
    // blinked: closing a smile would read as a glitch, not a blink.
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

    // The mood's own wording when the mood is what is on screen: one expression can
    // mean two things, and "something went wrong" after a polite refusal would
    // report a failure that never happened.
    val description = when {
        expressionOverride != null -> expression.contentDescription()
        emotion != null && expression != state.toExpression() -> emotion.contentDescription()
        else -> expression.contentDescription()
    }
    // Whatever is behind the face. The style-1 eyelid is drawn in this colour to
    // cut into the eye, so it has to match the surface exactly or the lid shows
    // up as a grey slab.
    val panelColor = backgroundColor ?: colors.background

    Box(modifier.semantics { contentDescription = description }) {
        // Every animated value below is read INSIDE the draw lambda on purpose:
        // a state read in the draw phase invalidates only the drawing, so the
        // ambient clock never triggers recomposition at 60fps.
        Canvas(Modifier.fillMaxSize()) {
            val face = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            val drift = sin(phase * 2f * PI.toFloat()) * face * 0.010f * driftAmount
            // Three mouth movements per ambient cycle: slow enough not to look like
            // a rattle, fast enough to read as speech.
            val talk = talkAmount * (0.30f + 0.30f * sin(phase * 2f * PI.toFloat() * 3f))
            // Only a round eye blinks; an arc or a heart has no lid to close.
            val blinkFactor = if (target.eyeStyle == EyeStyle.ROUND) blinkAnim else 1f

            // Each style decides where its screen is; the expression itself is drawn
            // the same way inside it, so a new expression appears in both styles.
            // Both styles draw straight onto the background; what differs is the
            // face, not the frame. A moulded head was tried and dropped — the
            // accessory drew more attention than the face it framed.
            val screen = computeScreen(face, center)
            val unit = screen.width / 6.4f

            drawDecorations(
                accent = eyeColor,
                unit = unit,
                center = center,
                shellRadius = screen.width * 0.72f,
                phase = phase,
                ringAmount = ringAmount,
                progressAmount = progressAmount,
                amplitude = amplitude,
            )

            // Everything the expression draws stays on the screen it belongs to.
            clipPath(screen.clipPath()) {
                translate(top = drift) {
                    rotate(degrees = headTilt, pivot = screen.center) {
                        when (faceStyle) {
                            FaceStyle.SCREEN -> drawFaceContent(
                                style = target.eyeStyle,
                                mouthShape = target.mouth,
                                eyeColor = eyeColor,
                                panelColor = panelColor,
                                unit = unit,
                                center = screen.center,
                                eyeOpen = eyeOpen * blinkFactor,
                                eyeSquint = eyeSquint,
                                eyeScale = eyeScale,
                                lidAngle = lidAngle,
                                mouthOpen = max(mouthOpen, talk),
                                mouthCurve = mouthCurve,
                                gazeX = gazeX,
                                gazeY = gazeY,
                            )

                            FaceStyle.DROID -> drawOledEyes(
                                eyes = expression.oledEyes(),
                                eyeColor = eyeColor,
                                panelColor = panelColor,
                                unit = unit,
                                center = screen.center,
                                blink = blinkAnim,
                                // A talking face on an eyes-only design has no mouth
                                // to move, so the voice lifts the eyes instead.
                                voice = max(mouthOpen, talk),
                                gazeX = gazeX,
                                gazeY = gazeY,
                            )
                        }
                    }
                }

                // Style 2 says everything with its eyes; an icon on it would be a
                // second language on the same face.
                if (faceStyle == FaceStyle.SCREEN) {
                    drawIcon(
                        icon = target.icon,
                        color = eyeColor,
                        unit = unit,
                        // Pulled in from the corner: the clip means an icon placed
                        // too far out loses half of itself to the rounded edge.
                        anchor = Offset(
                            screen.center.x + screen.width * 0.30f,
                            screen.center.y - screen.height * 0.28f,
                        ),
                        phase = phase,
                    )
                }
            }
        }
    }
}

/**
 * Where a style put its screen, so the expression knows how big to draw itself.
 *
 * [clipWidth]/[clipHeight]/[clipRadius] describe the surface the face is drawn
 * on. The eyelid is deliberately oversized — it has to stay opaque when rotated —
 * so without a clip it spills out of a visor and paints a black slab across the
 * robot's head.
 */
private data class FaceScreen(
    val center: Offset,
    val width: Float,
    val height: Float,
    val clipWidth: Float,
    val clipHeight: Float,
    val clipRadius: Float,
)

private fun FaceScreen.clipPath(): Path = Path().apply {
    addRoundRect(
        RoundRect(
            left = center.x - clipWidth / 2f,
            top = center.y - clipHeight / 2f,
            right = center.x + clipWidth / 2f,
            bottom = center.y + clipHeight / 2f,
            cornerRadius = CornerRadius(clipRadius, clipRadius),
        )
    )
}

/**
 * Where the face is drawn, and how far it may spill.
 *
 * Nothing is painted here on purpose. A filled panel looked right on the black
 * hands-free screen and showed up as a black card floating on the chat screen,
 * which is a slightly different black. The face now sits directly on whatever is
 * behind it.
 */
private fun computeScreen(face: Float, center: Offset): FaceScreen {
    val side = face * 0.98f
    return FaceScreen(
        center = center,
        width = side * 0.62f,
        height = side * 0.62f,
        clipWidth = side,
        clipHeight = side,
        clipRadius = side * 0.24f,
    )
}

/**
 * Style 2's face: two rectangles and nothing else.
 *
 * No gradient, no highlight, no mouth. The shape carries everything, the way it
 * does on a small monochrome display — which is what makes this a different face
 * rather than the same one in another frame.
 */
private fun DrawScope.drawOledEyes(
    eyes: OledEyeParams,
    eyeColor: Color,
    panelColor: Color,
    unit: Float,
    center: Offset,
    blink: Float,
    voice: Float,
    gazeX: Float,
    gazeY: Float,
) {
    val baseW = unit * 1.7f * eyes.width
    val baseH = unit * 2.45f
    val dx = unit * 1.85f * eyes.gap
    val cy = center.y + unit * eyes.offsetY + gazeY * unit * 0.5f
    val cx = center.x + gazeX * unit * 0.5f

    listOf(-1f to eyes.heightL, 1f to eyes.heightR).forEach { (side, heightScale) ->
        // The voice lifts the eyes a little while speaking; a face with no mouth
        // still has to look like it is the one talking.
        val h = max(unit * 0.22f, baseH * heightScale * blink * (1f + voice * 0.18f))
        val eyeCenter = Offset(cx + side * dx, cy)
        val topLeft = Offset(eyeCenter.x - baseW / 2f, eyeCenter.y - h / 2f)
        val radius = if (eyes.bottomHeavy) {
            // Flat on top, fully round underneath: the smiling-eye shape.
            CornerRadius(baseW * 0.18f, baseW * 0.18f)
        } else {
            CornerRadius(baseW * eyes.radius, baseW * eyes.radius)
        }

        // The eyelid CUTS the eye rather than being painted over it. Painting a
        // slab in the panel colour looked right on a flat panel and vanished
        // inside the visor, which has a gradient of its own.
        // Positive slope drops the OUTER corner (sad); negative drops the inner
        // one, which is the only way this face can look angry.
        val slope = (if (side < 0f) eyes.slopeL else eyes.slopeR) * side
        val drawEye = {
            if (eyes.bottomHeavy) {
                // A smiling eye is the eye with a bite taken out of the top: the
                // "^" shape. Drawing a rounded rect and adding a circle underneath
                // produced a fat pill instead, which read as no expression at all.
                val body = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = topLeft.x, top = topLeft.y,
                            right = topLeft.x + baseW, bottom = topLeft.y + h,
                            cornerRadius = CornerRadius(baseW * 0.30f, baseW * 0.30f),
                        )
                    )
                }
                val bite = Path().apply {
                    val r = baseW * 0.78f
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            center = Offset(eyeCenter.x, eyeCenter.y - h * 0.62f),
                            radius = r,
                        )
                    )
                }
                drawPath(Path().apply { op(body, bite, PathOperation.Difference) }, eyeColor)
            } else {
                drawRoundRect(color = eyeColor, topLeft = topLeft, size = Size(baseW, h), cornerRadius = radius)
            }
        }

        if (slope == 0f) {
            drawEye()
        } else {
            // The lid also bites into the eye, not just grazes its top corner.
            // Rotating a line that starts exactly at the top edge only shaves the
            // corner that the corner radius had already rounded away, and the
            // expression stayed invisible.
            val bite = h * (0.10f + kotlin.math.abs(slope) / 34f * 0.22f)
            clipPath(
                slantedLidClip(eyeCenter, slope, baseW * 2.2f, h * 2.4f, h * 0.5f - bite)
            ) { drawEye() }
        }
    }
}

/**
 * The area below a slanted eyelid, as a path.
 *
 * Built by rotating the four corners by hand rather than with a rotate() block,
 * because the clip has to persist for the drawing that follows it.
 */
private fun slantedLidClip(
    center: Offset,
    angleDeg: Float,
    width: Float,
    height: Float,
    lidTop: Float,
): Path {
    val rad = angleDeg * PI.toFloat() / 180f
    val cos = kotlin.math.cos(rad)
    val sin = kotlin.math.sin(rad)
    fun point(dx: Float, dy: Float) = Offset(
        center.x + dx * cos - dy * sin,
        center.y + dx * sin + dy * cos,
    )
    return Path().apply {
        val a = point(-width / 2f, -lidTop)
        val b = point(width / 2f, -lidTop)
        val c = point(width / 2f, height)
        val d = point(-width / 2f, height)
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }
}

private fun DrawScope.drawFaceContent(
    style: EyeStyle,
    mouthShape: MouthShape,
    eyeColor: Color,
    panelColor: Color,
    unit: Float,
    center: Offset,
    eyeOpen: Float,
    eyeSquint: Float,
    eyeScale: Float,
    lidAngle: Float,
    mouthOpen: Float,
    mouthCurve: Float,
    gazeX: Float,
    gazeY: Float,
) {
    val cx = center.x
    val cy = center.y - unit * 0.55f

    val eyeW = unit * 1.85f * eyeScale
    val eyeHFull = unit * 2.05f * eyeScale
    // A closed eye is a line, not a disappearance: keeping a sliver means a blink
    // reads as a blink instead of the eyes vanishing for two frames.
    val eyeH = max(unit * 0.16f, eyeHFull * eyeOpen)
    // Wide enough apart that the bloom of one never touches the other; adjacent
    // eyes read as a single bar rather than a face.
    val eyeDx = unit * 1.75f

    listOf(-1f, 1f).forEach { side ->
        val eyeCenter = Offset(cx + side * eyeDx + gazeX * unit * 0.22f, cy + gazeY * unit * 0.2f)
        when (style) {
            EyeStyle.ROUND -> {
                drawGlossyEye(eyeCenter, eyeW, eyeH, eyeColor, unit, gazeX, gazeY)
                drawLid(eyeCenter, eyeW, eyeH, panelColor, lidAngle * side * -1f, eyeSquint)
            }
            EyeStyle.ARC_UP -> drawArcEye(eyeCenter, eyeW, unit, eyeColor, up = true)
            EyeStyle.ARC_DOWN -> drawArcEye(eyeCenter, eyeW, unit, eyeColor, up = false)
            EyeStyle.LINE -> drawLine(
                color = eyeColor,
                start = Offset(eyeCenter.x - eyeW * 0.5f, eyeCenter.y),
                end = Offset(eyeCenter.x + eyeW * 0.5f, eyeCenter.y),
                strokeWidth = unit * 0.26f,
                cap = StrokeCap.Round,
            )
            EyeStyle.CROSS -> drawCrossEye(eyeCenter, eyeW, unit, eyeColor)
            EyeStyle.HEART -> drawHeartEye(eyeCenter, eyeW, eyeColor)
        }
    }

    drawMouth(
        shape = mouthShape,
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
private fun DrawScope.drawGlossyEye(
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
 * Angling it is the only way this face can look angry, so expressions use it
 * sparingly: a tilted lid on a thinking face reads as irritation, not thought.
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
    val coverage = height * (0.10f + drop * 0.5f)
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

/** A smiling (or downcast) eye: the kaomoji `^` and `v`. */
private fun DrawScope.drawArcEye(
    center: Offset,
    width: Float,
    unit: Float,
    color: Color,
    up: Boolean,
) {
    val half = width * 0.62f
    val rise = width * (if (up) 0.55f else -0.55f)
    val path = Path().apply {
        moveTo(center.x - half, center.y + rise * 0.45f)
        quadraticTo(center.x, center.y - rise * 0.75f, center.x + half, center.y + rise * 0.45f)
    }
    drawPath(path, color, style = Stroke(width = unit * 0.30f, cap = StrokeCap.Round))
}

/** Dizzy eyes. Reserved for a hard failure. */
private fun DrawScope.drawCrossEye(center: Offset, width: Float, unit: Float, color: Color) {
    val r = width * 0.42f
    val stroke = Stroke(width = unit * 0.26f, cap = StrokeCap.Round)
    drawPath(
        Path().apply {
            moveTo(center.x - r, center.y - r); lineTo(center.x + r, center.y + r)
            moveTo(center.x + r, center.y - r); lineTo(center.x - r, center.y + r)
        },
        color, style = stroke,
    )
}

private fun DrawScope.drawHeartEye(center: Offset, width: Float, color: Color) {
    val w = width * 1.05f
    val h = width * 0.95f
    val path = Path().apply {
        moveTo(center.x, center.y + h * 0.45f)
        cubicTo(
            center.x - w * 0.75f, center.y + h * 0.02f,
            center.x - w * 0.42f, center.y - h * 0.62f,
            center.x, center.y - h * 0.2f,
        )
        cubicTo(
            center.x + w * 0.42f, center.y - h * 0.62f,
            center.x + w * 0.75f, center.y + h * 0.02f,
            center.x, center.y + h * 0.45f,
        )
        close()
    }
    drawPath(path, color)
}

/**
 * The mouth. Closed it is a curved stroke whose bend carries the mood; open it
 * becomes a filled shape driven by the voice.
 */
private fun DrawScope.drawMouth(
    shape: MouthShape,
    center: Offset,
    unit: Float,
    color: Color,
    open: Float,
    curve: Float,
) {
    if (shape == MouthShape.NONE) return
    val width = unit * 1.75f
    val stroke = Stroke(width = unit * 0.22f, cap = StrokeCap.Round)

    if (open > 0.12f || shape == MouthShape.ROUND) {
        val height = unit * 1.6f * max(open, if (shape == MouthShape.ROUND) 0.45f else 0f)
        val w = if (shape == MouthShape.ROUND) height else width
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - w / 2f, center.y - height / 2f),
            size = Size(w, height),
            cornerRadius = CornerRadius(w * 0.45f, height * 0.45f),
        )
        return
    }

    when (shape) {
        MouthShape.LINE -> drawLine(
            color = color,
            start = Offset(center.x - width * 0.45f, center.y),
            end = Offset(center.x + width * 0.45f, center.y),
            strokeWidth = unit * 0.22f,
            cap = StrokeCap.Round,
        )

        MouthShape.WAVY -> {
            // Two opposed bends: the shape people read as a hesitant mouth.
            val q = width * 0.5f
            drawPath(
                Path().apply {
                    moveTo(center.x - width * 0.5f, center.y)
                    quadraticTo(center.x - q * 0.5f, center.y - unit * 0.35f, center.x, center.y)
                    quadraticTo(center.x + q * 0.5f, center.y + unit * 0.35f, center.x + width * 0.5f, center.y)
                },
                color, style = stroke,
            )
        }

        else -> {
            val bend = curve * unit * 0.75f
            drawPath(
                Path().apply {
                    moveTo(center.x - width / 2f, center.y - bend * 0.35f)
                    quadraticTo(center.x, center.y + bend, center.x + width / 2f, center.y - bend * 0.35f)
                },
                color, style = stroke,
            )
        }
    }
}

/**
 * The support icon in the panel's top-right corner.
 *
 * It says what a face cannot: that the agent is waiting on the user rather than
 * merely thinking, or that the last request failed. Kept small and in the corner
 * so it never competes with the eyes.
 */
private fun DrawScope.drawIcon(
    icon: FaceIcon,
    color: Color,
    unit: Float,
    anchor: Offset,
    phase: Float,
) {
    if (icon == FaceIcon.NONE) return
    val stroke = Stroke(width = unit * 0.16f, cap = StrokeCap.Round)

    when (icon) {
        FaceIcon.DOTS -> repeat(3) { i ->
            // Each dot peaks a third of a cycle after the previous one.
            val p = (phase - i * 0.33f + 1f) % 1f
            val glow = (sin(p * PI.toFloat()) * 0.9f + 0.1f).coerceIn(0f, 1f)
            drawCircle(
                color = color.copy(alpha = glow),
                radius = unit * 0.15f,
                center = Offset(anchor.x + (i - 1) * unit * 0.5f, anchor.y),
            )
        }

        FaceIcon.QUESTION -> {
            val r = unit * 0.34f
            drawPath(
                Path().apply {
                    moveTo(anchor.x - r * 0.85f, anchor.y - r * 0.55f)
                    quadraticTo(anchor.x + r * 1.5f, anchor.y - r * 1.8f, anchor.x + r * 0.1f, anchor.y + r * 0.15f)
                    lineTo(anchor.x + r * 0.05f, anchor.y + r * 0.6f)
                },
                color, style = stroke,
            )
            drawCircle(color, radius = unit * 0.11f, center = Offset(anchor.x + r * 0.05f, anchor.y + r * 1.25f))
        }

        FaceIcon.ALERT -> {
            drawLine(
                color = color,
                start = Offset(anchor.x, anchor.y - unit * 0.45f),
                end = Offset(anchor.x, anchor.y + unit * 0.18f),
                strokeWidth = unit * 0.18f,
                cap = StrokeCap.Round,
            )
            drawCircle(color, radius = unit * 0.11f, center = Offset(anchor.x, anchor.y + unit * 0.5f))
        }

        FaceIcon.SPARKLE -> listOf(
            Triple(0f, 0f, 1f),
            Triple(-0.75f, 0.55f, 0.6f),
            Triple(0.7f, 0.62f, 0.45f),
        ).forEach { (dx, dy, scale) ->
            drawSparkle(
                Offset(anchor.x + dx * unit, anchor.y + dy * unit),
                unit * 0.42f * scale,
                // Sparkles breathe out of phase with each other so the group
                // twinkles instead of pulsing as one block.
                color.copy(alpha = (0.55f + 0.45f * sin((phase + dx) * 2f * PI.toFloat())).coerceIn(0f, 1f)),
            )
        }

        FaceIcon.HEART -> drawHeartEye(anchor, unit * 0.75f, color)

        FaceIcon.SLEEP -> listOf(0.9f to 0.7f, 0.0f to 1.0f, -0.75f to 1.35f).forEachIndexed { i, (dx, scale) ->
            drawZ(
                Offset(anchor.x + dx * unit, anchor.y - i * unit * 0.55f),
                unit * 0.34f * scale,
                color.copy(alpha = 1f - i * 0.28f),
                stroke,
            )
        }

        FaceIcon.NONE -> Unit
    }
}

private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    // A four-pointed star with concave sides, drawn as two mirrored curves per
    // quadrant: the shape reads as a glint where a plain cross reads as a plus.
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + radius * 0.18f, center.y - radius * 0.18f, center.x + radius, center.y)
        quadraticTo(center.x + radius * 0.18f, center.y + radius * 0.18f, center.x, center.y + radius)
        quadraticTo(center.x - radius * 0.18f, center.y + radius * 0.18f, center.x - radius, center.y)
        quadraticTo(center.x - radius * 0.18f, center.y - radius * 0.18f, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawZ(center: Offset, size: Float, color: Color, stroke: Stroke) {
    drawPath(
        Path().apply {
            moveTo(center.x - size / 2f, center.y - size / 2f)
            lineTo(center.x + size / 2f, center.y - size / 2f)
            lineTo(center.x - size / 2f, center.y + size / 2f)
            lineTo(center.x + size / 2f, center.y + size / 2f)
        },
        color, style = stroke,
    )
}

/**
 * Rings and the progress sweep, behind the face.
 *
 * [phase] runs 0f..1f once per ambient cycle; each decoration derives its own
 * timing from it rather than owning a separate animation.
 */
private fun DrawScope.drawDecorations(
    accent: Color,
    unit: Float,
    center: Offset,
    shellRadius: Float,
    phase: Float,
    ringAmount: Float,
    progressAmount: Float,
    amplitude: Float,
) {
    // ── Listening: rings expanding outward from the face ──
    if (ringAmount > 0.01f) {
        // Sized from the eyes, not from the panel. Tied to the panel they grew
        // wider than the screen and read as two lines crossing it, not as a face
        // emitting anything.
        val baseRadius = unit * 3.3f
        // Two rings half a cycle apart read as a continuous emission rather than
        // a single pulse that restarts.
        listOf(0f, 0.5f).forEach { offset ->
            val p = (phase + offset) % 1f
            drawCircle(
                color = accent.copy(alpha = (1f - p) * 0.32f * ringAmount),
                radius = baseRadius + p * unit * (1.5f + amplitude * 2.0f),
                center = center,
                style = Stroke(width = unit * 0.08f),
            )
        }
    }

    // ── Executing: a thin progress bar sweeping under the face ──
    if (progressAmount > 0.01f) {
        val barY = center.y + shellRadius * 0.86f
        val halfW = unit * 2.6f
        val thickness = unit * 0.08f
        drawLine(
            color = accent.copy(alpha = 0.18f * progressAmount),
            start = Offset(center.x - halfW, barY),
            end = Offset(center.x + halfW, barY),
            strokeWidth = thickness,
            cap = StrokeCap.Round,
        )
        // Indeterminate: the head slides across and wraps, since the agent gives
        // no percentage — only "still working".
        val headLen = halfW * 0.5f
        val headStart = -halfW + phase * (halfW * 2f + headLen) - headLen
        drawLine(
            color = accent.copy(alpha = 0.9f * progressAmount),
            start = Offset(center.x + headStart.coerceAtLeast(-halfW), barY),
            end = Offset(center.x + (headStart + headLen).coerceAtMost(halfW), barY),
            strokeWidth = thickness,
            cap = StrokeCap.Round,
        )
    }
}
