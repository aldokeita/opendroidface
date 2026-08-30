// TUJUAN: app/src/main/java/com/opendroid/ai/ui/face/RobotFace.kt
//
// Wajah robot digambar dengan Canvas — sengaja TIDAK memakai Lottie di Fase 1.
// Alasannya: parameter wajah harus bisa digerakkan terus-menerus oleh amplitudo
// suara (Fase 2-3), dan itu jauh lebih mudah dengan geometri yang kita kendalikan
// sendiri daripada mengatur progress animasi Lottie.
// Lottie (`lottie-compose` sudah ada di build.gradle) disimpan untuk backlog
// "wajah kustom" — memuat karakter buatan pengguna dari file eksternal.
//
// CARA PAKAI di ui/screens/ChatScreen.kt:
//
//     val agentState by viewModel.visibleAgentState.collectAsState()
//     RobotFace(
//         state = agentState,
//         modifier = Modifier.fillMaxWidth().height(180.dp)
//     )

package com.opendroid.ai.ui.face

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.ui.theme.LocalOpenDroidColors
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.random.Random

private const val TRANSITION_MS = 320

@Composable
fun RobotFace(
    state: AgentState,
    modifier: Modifier = Modifier,
    /** Amplitudo suara 0f..1f. Fase 2-3 mengisi ini dari VoiceAmplitude.level. */
    amplitude: Float = 0f,
) {
    val colors = LocalOpenDroidColors.current
    val target = state.toExpression().params()

    // Setiap parameter dianimasikan terpisah supaya transisi antar-ekspresi terasa
    // organik, bukan "berganti gambar".
    val eyeOpen by animateFloatAsState(target.eyeOpen, tween(TRANSITION_MS), label = "eyeOpen")
    val eyeSquint by animateFloatAsState(target.eyeSquint, tween(TRANSITION_MS), label = "eyeSquint")
    val browAngle by animateFloatAsState(target.browAngle, tween(TRANSITION_MS), label = "browAngle")
    val browRaise by animateFloatAsState(target.browRaise, tween(TRANSITION_MS), label = "browRaise")
    val mouthCurve by animateFloatAsState(target.mouthCurve, tween(TRANSITION_MS), label = "mouthCurve")
    val headTilt by animateFloatAsState(target.headTilt, tween(TRANSITION_MS), label = "headTilt")
    val pupilX by animateFloatAsState(target.pupilOffsetX, tween(TRANSITION_MS), label = "pupilX")
    val pupilY by animateFloatAsState(target.pupilOffsetY, tween(TRANSITION_MS), label = "pupilY")

    // Mulut: saat bicara/mendengar ikut amplitudo, selain itu pakai nilai target.
    val liveMouth = when (state) {
        is AgentState.Speaking -> amplitude
        is AgentState.Listening -> amplitude * 0.35f
        else -> target.mouthOpen
    }
    val mouthOpen by animateFloatAsState(liveMouth, tween(60, easing = LinearEasing), label = "mouthOpen")

    // Kedip acak — hanya saat wajah tidak sedang menyipit fokus.
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

    val accent = when (target.accent) {
        FaceAccent.PRIMARY -> colors.accentNeonGreen
        FaceAccent.CYAN -> colors.accentCyan
        FaceAccent.PURPLE -> colors.accentPurple
        FaceAccent.ORANGE -> colors.accentOrange
        FaceAccent.RED -> colors.accentRed
    }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            rotate(degrees = headTilt, pivot = center) {
                drawFace(
                    accent = accent,
                    dim = colors.textSecondary,
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

private fun DrawScope.drawFace(
    accent: Color,
    dim: Color,
    eyeOpen: Float,
    eyeSquint: Float,
    browAngle: Float,
    browRaise: Float,
    mouthOpen: Float,
    mouthCurve: Float,
    pupilX: Float,
    pupilY: Float,
) {
    val unit = size.minDimension / 10f          // satuan skala; wajah "menggambar" pada grid 10x10
    val cx = size.width / 2f
    val cy = size.height / 2f

    val eyeDx = unit * 2f
    val eyeRadius = unit * 1.1f
    val eyeHeight = max(unit * 0.12f, eyeRadius * eyeOpen * (1f - eyeSquint * 0.55f))

    // ── Mata (kapsul membulat, tinggi mengikuti eyeOpen) ──
    listOf(-eyeDx, eyeDx).forEach { dx ->
        drawOval(
            color = accent,
            topLeft = Offset(cx + dx - eyeRadius, cy - eyeHeight),
            size = Size(eyeRadius * 2f, eyeHeight * 2f),
        )
        // Pupil hanya terlihat kalau mata cukup terbuka.
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

    // ── Alis ──
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

    // ── Mulut ──
    // Lebar tetap, tinggi mengikuti mouthOpen, lengkung mengikuti mouthCurve.
    val mouthW = unit * 2.4f
    val mouthH = max(unit * 0.18f, unit * 1.3f * mouthOpen)
    val mouthY = cy + unit * 2.4f + mouthCurve * unit * -0.3f
    drawOval(
        color = accent,
        topLeft = Offset(cx - mouthW / 2f, mouthY - mouthH / 2f),
        size = Size(mouthW, mouthH),
    )
}
