// Style 2's face: eyes only, in the manner of Anki's Cozmo and the esp32-eyes
// library (github.com/playfultechnology/esp32-eyes).
//
// That family of faces has no mouth and no icons. Everything is said by two
// rectangles: how tall they are, how wide, how far apart, where they sit, and
// how the eyelid slices across the top. It is a genuinely different vocabulary
// from style 1, not the same face in a different frame — which is the point of
// offering two.
//
// Only the shapes are borrowed. No code from that library is used here; it is
// C++ for a 128x64 OLED.

package com.opendroid.ai.ui.face

/**
 * Geometry of one pair of OLED eyes.
 *
 * Eyes are described independently, left and right, because asymmetry is most of
 * what separates "curious" from "confused" on a face with no other features.
 *
 * @param heightL,heightR  tinggi mata, kelipatan tinggi dasar
 * @param width            lebar mata, kelipatan lebar dasar
 * @param gap              jarak antar mata, kelipatan jarak dasar
 * @param offsetY          geser vertikal, dalam unit wajah
 * @param slopeL,slopeR    derajat kemiringan kelopak. POSITIF = sudut LUAR turun
 *                         (sedih/pasrah), NEGATIF = sudut DALAM turun (marah).
 *                         Tanda ini satu-satunya pembeda sedih dan marah.
 * @param bottomHeavy      true = bagian bawah membulat penuh, atas rata —
 *                         bentuk mata tersenyum
 * @param radius           kelengkungan sudut, pecahan dari lebar
 */
data class OledEyeParams(
    val heightL: Float = 1f,
    val heightR: Float = 1f,
    val width: Float = 1f,
    val gap: Float = 1f,
    val offsetY: Float = 0f,
    val slopeL: Float = 0f,
    val slopeR: Float = 0f,
    val bottomHeavy: Boolean = false,
    val radius: Float = 0.34f,
)

fun FaceExpression.oledEyes(): OledEyeParams = when (this) {
    FaceExpression.NEUTRAL -> OledEyeParams()

    FaceExpression.LISTENING -> OledEyeParams(
        heightL = 1.15f, heightR = 1.15f, width = 1.05f,
    )

    // Berpikir: mata mengecil dan naik, pandangan dibawa ke atas-samping oleh
    // FaceParams.gaze. Tidak ada kemiringan kelopak sama sekali — itu yang
    // membuat wajah terbaca marah.
    FaceExpression.THINKING -> OledEyeParams(
        heightL = 0.85f, heightR = 0.85f, offsetY = -0.12f,
    )

    // Penasaran: satu mata lebih besar dari yang lain. Pada wajah tanpa mulut,
    // asimetri inilah yang menggantikan alis terangkat sebelah.
    FaceExpression.CURIOUS -> OledEyeParams(
        heightL = 0.8f, heightR = 1.15f, gap = 1.05f,
    )

    FaceExpression.FOCUSED -> OledEyeParams(
        heightL = 0.5f, heightR = 0.5f, width = 1.05f,
    )

    // Bicara: mata sedikit lebih tinggi dari netral, lalu ikut naik-turun mengikuti
    // suara saat digambar. Perbedaan statisnya kecil tapi harus ada — dua ekspresi
    // dengan geometri identik tidak bisa dibedakan saat suaranya diam.
    FaceExpression.SPEAKING -> OledEyeParams(
        heightL = 1.06f, heightR = 1.06f, gap = 0.97f,
    )

    // Sedih: mata mengecil, turun, sudut LUAR jatuh.
    FaceExpression.SAD -> OledEyeParams(
        heightL = 0.78f, heightR = 0.78f, offsetY = 0.18f, radius = 0.2f,
        slopeL = 28f, slopeR = 28f,
    )

    // Senang: bagian atas mata digigit lengkung — bentuk "^ ^".
    FaceExpression.HAPPY -> OledEyeParams(
        heightL = 0.85f, heightR = 0.85f, width = 1.15f,
        bottomHeavy = true, radius = 0.3f,
    )

    FaceExpression.CONFUSED -> OledEyeParams(
        heightL = 1.05f, heightR = 0.65f,
        slopeL = 12f, slopeR = -8f,
    )

    FaceExpression.SLEEPY -> OledEyeParams(
        heightL = 0.22f, heightR = 0.22f, width = 1.05f, offsetY = 0.22f,
    )

    FaceExpression.SURPRISED -> OledEyeParams(
        heightL = 1.35f, heightR = 1.35f, width = 1.15f, gap = 1.08f,
        radius = 0.42f,
    )

    // Sayang: mata tinggi dan rapat, atas digigit lengkung — pada wajah tanpa
    // mulut ini membaca sebagai wajah berbinar.
    FaceExpression.LOVE -> OledEyeParams(
        heightL = 1.15f, heightR = 1.15f, width = 1.12f, gap = 0.86f,
        bottomHeavy = true, radius = 0.45f,
    )

    FaceExpression.GLEE -> OledEyeParams(
        heightL = 0.95f, heightR = 0.95f, width = 1.25f, gap = 0.95f,
        bottomHeavy = true, radius = 0.3f, offsetY = -0.05f,
    )

    FaceExpression.AWE -> OledEyeParams(
        heightL = 1.28f, heightR = 1.28f, width = 1.05f, gap = 0.92f,
        offsetY = -0.1f, radius = 0.48f,
    )

    FaceExpression.SCARED -> OledEyeParams(
        heightL = 1.4f, heightR = 1.4f, width = 0.92f, gap = 1.1f,
        offsetY = 0.08f, radius = 0.46f,
    )

    // Cemas: mata kecil, turun, sudut luar sedikit jatuh — sedih yang belum jadi.
    FaceExpression.WORRIED -> OledEyeParams(
        heightL = 0.8f, heightR = 0.86f, offsetY = 0.12f,
        slopeL = 14f, slopeR = 14f,
    )

    // Ragu: satu mata menyipit, satu terbuka. Inilah alis terangkat sebelah pada
    // wajah yang tidak punya alis.
    FaceExpression.SKEPTIC -> OledEyeParams(
        heightL = 1.05f, heightR = 0.55f, gap = 1.02f,
        slopeR = 10f,
    )

    FaceExpression.SUSPICIOUS -> OledEyeParams(
        heightL = 0.45f, heightR = 0.45f, width = 1.12f, gap = 0.95f,
        slopeL = -6f, slopeR = -6f,
    )

    FaceExpression.UNIMPRESSED -> OledEyeParams(
        heightL = 0.38f, heightR = 0.38f, width = 1.15f, offsetY = -0.06f,
    )

    // Marah dan turunannya: sudut DALAM yang jatuh. Besarnya sudut itulah bedanya
    // kesal, marah, dan murka.
    FaceExpression.ANNOYED -> OledEyeParams(
        heightL = 0.72f, heightR = 0.72f, radius = 0.2f,
        slopeL = -16f, slopeR = -16f,
    )

    FaceExpression.ANGRY -> OledEyeParams(
        heightL = 0.9f, heightR = 0.9f, width = 1.05f, radius = 0.16f,
        slopeL = -30f, slopeR = -30f,
    )

    FaceExpression.FURIOUS -> OledEyeParams(
        heightL = 1.05f, heightR = 1.05f, width = 1.12f, gap = 0.95f, radius = 0.12f,
        slopeL = -40f, slopeR = -40f,
    )

    // Frustrasi: marah yang menyerah — sudut dalam jatuh, tapi matanya mengecil
    // dan turun, bukan membesar.
    FaceExpression.FRUSTRATED -> OledEyeParams(
        heightL = 0.6f, heightR = 0.6f, width = 1.08f, offsetY = 0.14f, radius = 0.18f,
        slopeL = -24f, slopeR = -24f,
    )

    FaceExpression.SQUINT -> OledEyeParams(
        heightL = 0.34f, heightR = 0.34f, width = 1.2f, gap = 0.92f,
    )
}
