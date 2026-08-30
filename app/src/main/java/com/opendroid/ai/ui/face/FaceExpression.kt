// TUJUAN: app/src/main/java/com/opendroid/ai/ui/face/FaceExpression.kt
//
// Pemetaan murni AgentState -> ekspresi wajah. Sengaja dipisah dari Composable
// supaya bisa di-unit-test tanpa Compose test runtime.

package com.opendroid.ai.ui.face

import com.opendroid.ai.core.agent.AgentState

/**
 * Bentuk dasar ekspresi wajah. Ditentukan sepenuhnya oleh [AgentState].
 *
 * Emosi dari LLM (Fase 4) hanya MEMODULASI parameter di [FaceParams], tidak pernah
 * mengganti nilai enum ini — wajah "senang" saat agent error akan terasa rusak.
 */
enum class FaceExpression {
    NEUTRAL,
    LISTENING,
    THINKING,
    CURIOUS,
    FOCUSED,
    SPEAKING,
    SAD,
}

fun AgentState.toExpression(): FaceExpression = when (this) {
    is AgentState.Idle -> FaceExpression.NEUTRAL
    is AgentState.Listening -> FaceExpression.LISTENING
    is AgentState.Thinking -> FaceExpression.THINKING
    is AgentState.PlanProposed -> FaceExpression.CURIOUS
    is AgentState.ExecutingPlan -> FaceExpression.FOCUSED
    is AgentState.Speaking -> FaceExpression.SPEAKING
    is AgentState.Error -> FaceExpression.SAD
}

/**
 * Text equivalent of the expression, for TalkBack. The face is the only status
 * indicator in Auto mode, so without this a screen reader user gets nothing.
 */
fun FaceExpression.contentDescription(): String = when (this) {
    FaceExpression.NEUTRAL -> "Assistant face: idle"
    FaceExpression.LISTENING -> "Assistant face: listening"
    FaceExpression.THINKING -> "Assistant face: thinking"
    FaceExpression.CURIOUS -> "Assistant face: waiting for your approval"
    FaceExpression.FOCUSED -> "Assistant face: running your request"
    FaceExpression.SPEAKING -> "Assistant face: speaking"
    FaceExpression.SAD -> "Assistant face: something went wrong"
}

/**
 * Parameter geometri wajah yang dianimasikan. Semua ternormalisasi supaya
 * transisi antar-ekspresi bisa dilakukan dengan interpolasi linear sederhana.
 *
 * @param eyeOpen         0f = terpejam, 1f = normal, >1f = membelalak
 * @param eyeSquint       0f = tidak menyipit, 1f = menyipit penuh (fokus)
 * @param browAngle       derajat; negatif = ujung dalam turun (sedih/marah), positif = terangkat
 * @param browRaise       0f = normal, 1f = terangkat penuh (terkejut/mendengar)
 * @param mouthOpen       0f = tertutup, 1f = terbuka penuh
 * @param mouthCurve      -1f = cemberut, 0f = datar, 1f = tersenyum
 * @param headTilt        derajat kemiringan kepala
 * @param pupilOffsetX    -1f..1f, arah pandang horizontal
 * @param pupilOffsetY    -1f..1f, arah pandang vertikal (negatif = ke atas)
 * @param accent          pilihan warna aksen, lihat [FaceAccent]
 */
data class FaceParams(
    val eyeOpen: Float = 1f,
    val eyeSquint: Float = 0f,
    val browAngle: Float = 0f,
    val browRaise: Float = 0f,
    val mouthOpen: Float = 0f,
    val mouthCurve: Float = 0f,
    val headTilt: Float = 0f,
    val pupilOffsetX: Float = 0f,
    val pupilOffsetY: Float = 0f,
    val accent: FaceAccent = FaceAccent.PRIMARY,
)

enum class FaceAccent { PRIMARY, CYAN, PURPLE, ORANGE, RED }

/** Target statis tiap ekspresi. Animasi = interpolasi menuju nilai ini. */
fun FaceExpression.params(): FaceParams = when (this) {
    FaceExpression.NEUTRAL -> FaceParams(
        eyeOpen = 1f,
        mouthCurve = 0.15f,
    )

    FaceExpression.LISTENING -> FaceParams(
        eyeOpen = 1.25f,
        browRaise = 0.5f,
        mouthCurve = 0.1f,
        accent = FaceAccent.CYAN,
    )

    FaceExpression.THINKING -> FaceParams(
        eyeOpen = 0.85f,
        browAngle = 8f,
        pupilOffsetX = 0.5f,
        pupilOffsetY = -0.6f,
        headTilt = -4f,
        accent = FaceAccent.PURPLE,
    )

    FaceExpression.CURIOUS -> FaceParams(
        eyeOpen = 1.15f,
        browRaise = 0.7f,
        browAngle = 10f,
        mouthCurve = 0.2f,
        headTilt = 8f,
        accent = FaceAccent.CYAN,
    )

    FaceExpression.FOCUSED -> FaceParams(
        eyeOpen = 0.7f,
        eyeSquint = 0.6f,
        browAngle = -5f,
        accent = FaceAccent.ORANGE,
    )

    FaceExpression.SPEAKING -> FaceParams(
        eyeOpen = 1f,
        mouthOpen = 0.4f,   // Fase 3 menimpa nilai ini dengan amplitudo nyata
        mouthCurve = 0.2f,
    )

    FaceExpression.SAD -> FaceParams(
        eyeOpen = 0.6f,
        browAngle = -18f,
        mouthCurve = -0.5f,
        headTilt = -6f,
        accent = FaceAccent.RED,
    )
}
