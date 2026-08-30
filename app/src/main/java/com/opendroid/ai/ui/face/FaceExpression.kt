// TUJUAN: app/src/main/java/com/opendroid/ai/ui/face/FaceExpression.kt
//
// Pemetaan murni AgentState -> ekspresi wajah. Sengaja dipisah dari Composable
// supaya bisa di-unit-test tanpa Compose test runtime.

package com.opendroid.ai.ui.face

import com.opendroid.ai.core.agent.AgentState

/**
 * Bentuk dasar ekspresi wajah.
 *
 * Tujuh yang pertama ditentukan sepenuhnya oleh [AgentState]. Sisanya belum
 * punya pemicu — itu kosakata untuk Fase 4, saat emosi dari LLM memodulasi
 * wajah di atas ekspresi dasar. Emosi tidak boleh menimpa state: wajah "senang"
 * saat agent error akan terasa rusak.
 */
enum class FaceExpression {
    // ── Terhubung ke AgentState ──
    NEUTRAL,
    LISTENING,
    THINKING,
    CURIOUS,
    FOCUSED,
    SPEAKING,
    SAD,

    // ── Kosakata emosi (Fase 4) ──
    // Nama-nama di bawah mengikuti daftar 18 emosi milik esp32-eyes
    // (github.com/playfultechnology/esp32-eyes, FaceEmotions.hpp), ditambah
    // beberapa yang kita butuhkan sendiri. Hanya nama dan bentuknya yang
    // diambil sebagai rujukan — tidak ada kode dari sana yang dipakai.
    HAPPY,
    GLEE,
    LOVE,
    AWE,
    SURPRISED,
    SCARED,
    WORRIED,
    CONFUSED,
    SKEPTIC,
    SUSPICIOUS,
    UNIMPRESSED,
    ANNOYED,
    ANGRY,
    FURIOUS,
    FRUSTRATED,
    SQUINT,
    SLEEPY,
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
 * The state the face should show, which is not always the agent's own state.
 *
 * The microphone opens before the agent knows anything about it: the recognizer
 * is already recording while [AgentState] is still Idle. Without this the face
 * sits there neutral through the entire time the user is talking, which is
 * exactly the moment it most needs to look like it is listening.
 *
 * Only the two resting states are overridden. Idle is obvious; Error is included
 * because it never clears on its own — after a failed request the agent stays in
 * Error, so a face that kept it would sit there looking sad through the user's
 * next sentence while the status line says "Listening…". Thinking, executing and
 * speaking are live states and must never be masked by the microphone.
 */
fun faceStateFor(agentState: AgentState, micOpen: Boolean): AgentState = when {
    !micOpen -> agentState
    agentState is AgentState.Idle || agentState is AgentState.Error -> AgentState.Listening
    else -> agentState
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
    FaceExpression.HAPPY -> "Assistant face: pleased"
    FaceExpression.GLEE -> "Assistant face: delighted"
    FaceExpression.LOVE -> "Assistant face: fond"
    FaceExpression.AWE -> "Assistant face: impressed"
    FaceExpression.SURPRISED -> "Assistant face: surprised"
    FaceExpression.SCARED -> "Assistant face: alarmed"
    FaceExpression.WORRIED -> "Assistant face: worried"
    FaceExpression.CONFUSED -> "Assistant face: unsure"
    FaceExpression.SKEPTIC -> "Assistant face: doubtful"
    FaceExpression.SUSPICIOUS -> "Assistant face: suspicious"
    FaceExpression.UNIMPRESSED -> "Assistant face: unimpressed"
    FaceExpression.ANNOYED -> "Assistant face: annoyed"
    FaceExpression.ANGRY -> "Assistant face: angry"
    FaceExpression.FURIOUS -> "Assistant face: furious"
    FaceExpression.FRUSTRATED -> "Assistant face: frustrated"
    FaceExpression.SQUINT -> "Assistant face: peering"
    FaceExpression.SLEEPY -> "Assistant face: idle for a while"
}

/**
 * Bentuk mata. Ini pembawa utama emosi.
 *
 * Versi pertama wajah ini hanya punya satu bentuk mata dan memiringkan kelopak
 * untuk semua emosi — hasilnya setiap ekspresi non-netral terbaca sebagai marah.
 * Mata yang benar-benar berganti bentuk jauh lebih terbaca daripada kelopak
 * miring, dan itu pula yang dipakai wajah robot bergaya layar pada umumnya.
 */
enum class EyeStyle {
    /** Kapsul mengilap dengan gradasi dan pantulan cahaya. Bentuk dasar. */
    ROUND,
    /** Lengkung ke atas — mata tersenyum. */
    ARC_UP,
    /** Lengkung ke bawah — sedih, memelas. */
    ARC_DOWN,
    /** Garis mendatar — mengantuk, malas. */
    LINE,
    /** Silang — pusing, gagal total. */
    CROSS,
    /** Hati — senang berlebihan. */
    HEART,
}

/** Bentuk mulut. */
enum class MouthShape {
    /** Tidak ada mulut — wajah hanya mata. */
    NONE,
    /** Garis lurus tipis. */
    LINE,
    /** Lengkung; arah dan kedalaman dari [FaceParams.mouthCurve]. */
    CURVE,
    /** Bulat kecil — terkejut. */
    ROUND,
    /** Bergelombang — bingung, ragu. */
    WAVY,
}

/**
 * Ikon kecil di sudut panel. Menyampaikan hal yang tidak bisa disampaikan
 * bentuk wajah — bahwa agent sedang menunggu jawaban, misalnya, bukan sekadar
 * "sedang berpikir".
 */
enum class FaceIcon {
    NONE,
    /** Tiga titik berjalan — sedang memproses. */
    DOTS,
    /** Tanda tanya — menunggu keputusan pengguna. */
    QUESTION,
    /** Tanda seru — ada yang salah. */
    ALERT,
    /** Kilau — hasil bagus, senang. */
    SPARKLE,
    /** Hati. */
    HEART,
    /** Zzz — lama menganggur. */
    SLEEP,
}

/**
 * Parameter geometri wajah yang dianimasikan. Semua ternormalisasi supaya
 * transisi antar-ekspresi bisa dilakukan dengan interpolasi linear sederhana.
 *
 * @param eyeStyle        bentuk mata; pembawa utama emosi
 * @param eyeOpen         0f = terpejam, 1f = normal, >1f = membelalak
 * @param eyeSquint       0f = tidak menyipit, 1f = kelopak turun penuh
 * @param lidAngle        derajat kemiringan kelopak; NEGATIF = ujung dalam turun (marah).
 *                        Pakai hemat — ini satu-satunya sumber kesan "marah" di wajah ini.
 * @param eyeScale        pengali ukuran mata; >1f = membelalak
 * @param mouth           bentuk mulut
 * @param mouthOpen       0f = tertutup, 1f = terbuka penuh
 * @param mouthCurve      -1f = cemberut, 0f = datar, 1f = tersenyum
 * @param headTilt        derajat kemiringan kepala
 * @param gazeX           -1f..1f, arah pandang horizontal
 * @param gazeY           -1f..1f, arah pandang vertikal (negatif = ke atas)
 * @param icon            ikon pendukung di sudut panel
 */
data class FaceParams(
    val eyeStyle: EyeStyle = EyeStyle.ROUND,
    val eyeOpen: Float = 1f,
    val eyeSquint: Float = 0f,
    val lidAngle: Float = 0f,
    val eyeScale: Float = 1f,
    val mouth: MouthShape = MouthShape.CURVE,
    val mouthOpen: Float = 0f,
    val mouthCurve: Float = 0f,
    val headTilt: Float = 0f,
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val icon: FaceIcon = FaceIcon.NONE,
)

/** Target statis tiap ekspresi. Animasi = interpolasi menuju nilai ini. */
fun FaceExpression.params(): FaceParams = when (this) {
    FaceExpression.NEUTRAL -> FaceParams(
        mouth = MouthShape.CURVE,
        mouthCurve = 0.25f,
    )

    FaceExpression.LISTENING -> FaceParams(
        eyeScale = 1.12f,
        gazeY = -0.1f,
        mouth = MouthShape.LINE,
    )

    // Berpikir: pandangan naik ke samping, kepala miring sedikit, titik berjalan.
    // Kelopak SENGAJA tidak dimiringkan — itu yang bikin versi sebelumnya terbaca
    // sebagai marah, bukan sebagai sedang memikirkan sesuatu.
    FaceExpression.THINKING -> FaceParams(
        eyeScale = 0.95f,
        gazeX = 0.55f,
        gazeY = -0.65f,
        headTilt = -5f,
        mouth = MouthShape.LINE,
        icon = FaceIcon.DOTS,
    )

    FaceExpression.CURIOUS -> FaceParams(
        eyeScale = 1.1f,
        headTilt = 9f,
        gazeY = -0.15f,
        mouth = MouthShape.CURVE,
        mouthCurve = 0.2f,
        icon = FaceIcon.QUESTION,
    )

    // Fokus: mata menyipit dari bawah kelopak, tanpa sudut marah.
    FaceExpression.FOCUSED -> FaceParams(
        eyeSquint = 0.45f,
        eyeScale = 0.92f,
        mouth = MouthShape.LINE,
    )

    FaceExpression.SPEAKING -> FaceParams(
        mouth = MouthShape.CURVE,
        mouthOpen = 0.4f,   // Fase 3 menimpa nilai ini dengan amplitudo nyata
        mouthCurve = 0.25f,
    )

    // Sedih/gagal: mata tetap bulat tapi sayu — kelopak turun, pandangan jatuh.
    // Mata lengkung ke bawah terbaca sebagai alis besar, bukan mata yang sayu.
    FaceExpression.SAD -> FaceParams(
        eyeScale = 0.92f,
        eyeSquint = 0.5f,
        mouth = MouthShape.CURVE,
        mouthCurve = -0.5f,
        headTilt = -4f,
        gazeY = 0.45f,
        icon = FaceIcon.ALERT,
    )

    FaceExpression.HAPPY -> FaceParams(
        eyeStyle = EyeStyle.ARC_UP,
        mouth = MouthShape.CURVE,
        mouthCurve = 0.75f,
        icon = FaceIcon.SPARKLE,
    )

    FaceExpression.GLEE -> FaceParams(
        eyeStyle = EyeStyle.ARC_UP,
        eyeScale = 1.1f,
        mouth = MouthShape.ROUND,
        mouthOpen = 0.7f,
        mouthCurve = 0.9f,
        icon = FaceIcon.SPARKLE,
    )

    FaceExpression.AWE -> FaceParams(
        eyeScale = 1.25f,
        gazeY = -0.25f,
        mouth = MouthShape.ROUND,
        mouthOpen = 0.35f,
        icon = FaceIcon.SPARKLE,
    )

    FaceExpression.SCARED -> FaceParams(
        eyeScale = 1.35f,
        eyeOpen = 1.1f,
        mouth = MouthShape.WAVY,
        headTilt = -3f,
        gazeY = 0.15f,
        icon = FaceIcon.ALERT,
    )

    FaceExpression.WORRIED -> FaceParams(
        eyeScale = 1.05f,
        eyeSquint = 0.2f,
        gazeX = -0.35f,
        gazeY = 0.2f,
        mouth = MouthShape.WAVY,
        headTilt = 3f,
    )

    FaceExpression.CONFUSED -> FaceParams(
        eyeScale = 0.98f,
        gazeX = -0.5f,
        gazeY = -0.3f,
        headTilt = 7f,
        mouth = MouthShape.WAVY,
        icon = FaceIcon.QUESTION,
    )

    FaceExpression.SKEPTIC -> FaceParams(
        eyeScale = 0.95f,
        eyeSquint = 0.35f,
        gazeX = 0.35f,
        mouth = MouthShape.LINE,
        headTilt = 4f,
        icon = FaceIcon.QUESTION,
    )

    FaceExpression.SUSPICIOUS -> FaceParams(
        eyeScale = 0.9f,
        eyeSquint = 0.55f,
        gazeX = 0.6f,
        mouth = MouthShape.LINE,
    )

    FaceExpression.UNIMPRESSED -> FaceParams(
        eyeStyle = EyeStyle.LINE,
        eyeOpen = 0.7f,
        eyeSquint = 0.3f,
        mouth = MouthShape.CURVE,
        mouthCurve = -0.2f,
    )

    FaceExpression.ANNOYED -> FaceParams(
        eyeScale = 0.92f,
        eyeSquint = 0.45f,
        mouth = MouthShape.CURVE,
        mouthCurve = -0.3f,
        gazeX = -0.25f,
    )

    FaceExpression.ANGRY -> FaceParams(
        eyeScale = 0.95f,
        eyeSquint = 0.4f,
        mouth = MouthShape.CURVE,
        mouthCurve = -0.6f,
    )

    FaceExpression.FURIOUS -> FaceParams(
        eyeScale = 1.05f,
        eyeSquint = 0.5f,
        mouth = MouthShape.ROUND,
        mouthOpen = 0.55f,
        mouthCurve = -0.8f,
        icon = FaceIcon.ALERT,
    )

    FaceExpression.FRUSTRATED -> FaceParams(
        eyeStyle = EyeStyle.CROSS,
        mouth = MouthShape.WAVY,
        headTilt = -2f,
        icon = FaceIcon.ALERT,
    )

    FaceExpression.SQUINT -> FaceParams(
        eyeScale = 0.85f,
        eyeSquint = 0.7f,
        gazeX = 0.15f,
        mouth = MouthShape.LINE,
    )

    // Mengantuk: garis mata mendatar. Kepala hampir tidak dimiringkan — kemiringan
    // ikut memutar garis matanya, dan garis miring terbaca sebagai wajah rusak.
    FaceExpression.SLEEPY -> FaceParams(
        eyeStyle = EyeStyle.LINE,
        eyeOpen = 0.5f,
        eyeSquint = 0.6f,
        eyeScale = 0.9f,
        mouth = MouthShape.LINE,
        headTilt = -1.5f,
        icon = FaceIcon.SLEEP,
    )

    FaceExpression.SURPRISED -> FaceParams(
        eyeScale = 1.3f,
        eyeOpen = 1.15f,
        mouth = MouthShape.ROUND,
        mouthOpen = 0.5f,
    )

    FaceExpression.LOVE -> FaceParams(
        eyeStyle = EyeStyle.HEART,
        mouth = MouthShape.CURVE,
        mouthCurve = 0.7f,
        icon = FaceIcon.HEART,
    )
}
