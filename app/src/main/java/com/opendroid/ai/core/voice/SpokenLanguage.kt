// Which language a spoken answer should be read in.
//
// Android reads text with the phonetics of whatever locale the engine is set
// to. A phone set to en-US reading "Sudah kupasang alarmnya jam enam" produces
// English sounds shaped like Indonesian words - the words are right and the
// pronunciation is wrong.
//
// The microphone already has a language setting (ui/face/VoiceLanguage.kt) and
// the mouth should honour the same one. But that setting defaults to "follow
// the device", and the device here is English while the conversation is not, so
// following it alone would leave the common case broken. When nothing has been
// chosen, the text itself decides.
//
// This is a function-word count, not language identification. It is meant to
// separate two languages that share an alphabet, on sentences a chat assistant
// actually produces, and to abstain when it cannot tell.

package com.opendroid.ai.core.voice

import java.util.Locale

object SpokenLanguage {

    val INDONESIAN: Locale = Locale.forLanguageTag("id-ID")

    /**
     * @param preferredTag the language the user picked, or null to follow the
     * device and, failing that, the text.
     */
    fun localeFor(text: String, preferredTag: String?, deviceLocale: Locale = Locale.getDefault()): Locale {
        preferredTag?.takeIf { it.isNotBlank() }?.let { return Locale.forLanguageTag(it) }
        if (deviceLocale.language == INDONESIAN.language) return deviceLocale
        return if (looksIndonesian(text)) INDONESIAN else deviceLocale
    }

    /**
     * True when the text carries more Indonesian evidence than English.
     *
     * Markers are weighted rather than counted, because a two-word command is
     * as much a sentence as a paragraph is. "Buka WhatsApp" has one Indonesian
     * word in it and no doubt about the language; "Meeting di Zoom" also has
     * one, and is a sentence an English speaker in Jakarta says every day. So
     * words that only ever appear in Indonesian carry the decision alone, and
     * words that merely lean that way need company.
     */
    fun looksIndonesian(text: String): Boolean {
        var indonesian = 0
        var english = 0
        for (word in WORD.split(text.lowercase(Locale.ROOT))) {
            if (word.isEmpty()) continue
            if (word in STRONG_INDONESIAN) indonesian += DECISIVE
            else if (word in WEAK_INDONESIAN) indonesian += SUPPORTING
            if (word in ENGLISH_MARKERS) english += DECISIVE
        }
        return indonesian >= DECISIVE && indonesian > english
    }

    private const val DECISIVE = 2
    private const val SUPPORTING = 1

    private val WORD = Regex("[^\\p{L}]+")

    /**
     * Words that settle it on their own: function words and the imperative
     * verbs a person gives a phone. None of them is an English word, so one
     * occurrence is not a coincidence.
     */
    private val STRONG_INDONESIAN = setOf(
        // function words
        "yang", "dan", "tidak", "saya", "kamu", "aku", "untuk",
        "dengan", "sudah", "bisa", "akan", "atau", "juga", "kalau", "tapi",
        "karena", "jangan", "kita", "mereka", "adalah", "saja", "lagi", "hanya",
        "pada", "dalam", "sedang", "belum", "harus", "mau", "ingin",
        "banyak", "sekarang", "nanti", "dari", "siapa", "bagaimana",
        "kenapa", "berapa", "sedikit", "semua", "setiap", "sendiri", "milik",
        "punya", "biar", "supaya", "agar", "lalu", "kemudian", "jadi",
        "masih", "pernah", "selalu", "sering", "kadang", "dulu", "tolong",
        // the commands this app exists to receive
        "buka", "bukakan", "tutup", "nyalakan", "matikan", "hidupkan",
        "kirim", "kirimkan", "panggil", "telepon", "hubungi", "putar",
        "setel", "pasang", "hapus", "cari", "carikan", "tampilkan",
        "bacakan", "baca", "ambil", "ubah", "naikkan", "turunkan",
        "buatkan", "catat", "ingatkan", "jadwalkan", "bikin", "sebutkan",
        "berhenti", "lanjutkan", "ulangi", "batalkan",
    )

    /**
     * Words that lean Indonesian but survive in English sentences a bilingual
     * speaker writes - "Meeting di Zoom", "ada update?". They need company.
     */
    private val WEAK_INDONESIAN = setOf(
        "ke", "di", "ini", "itu", "apa", "ada", "buat",
    )

    private val ENGLISH_MARKERS = setOf(
        "the", "and", "is", "are", "was", "were", "you", "your", "to", "of",
        "for", "that", "this", "with", "have", "has", "will", "would", "should",
        "not", "but", "from", "they", "them", "what", "which", "when", "where",
        "how", "there", "here", "been", "being", "can", "could", "about",
    )
}
