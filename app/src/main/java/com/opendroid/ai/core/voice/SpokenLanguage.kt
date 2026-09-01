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
     * True when the text carries more Indonesian function words than English
     * ones. Two matches are required, so a single ambiguous token - a name, a
     * loanword - cannot decide it, and a text with no evidence either way
     * returns false rather than guessing.
     */
    fun looksIndonesian(text: String): Boolean {
        var indonesian = 0
        var english = 0
        for (word in WORD.split(text.lowercase(Locale.ROOT))) {
            if (word.isEmpty()) continue
            if (word in INDONESIAN_MARKERS) indonesian++
            if (word in ENGLISH_MARKERS) english++
        }
        return indonesian >= 2 && indonesian > english
    }

    private val WORD = Regex("[^\\p{L}]+")

    /**
     * Function words, not vocabulary: they appear in almost any sentence of
     * their language and almost never in the other one. Deliberately excludes
     * words English also has ("ada" is not here for the same reason "in" is
     * not - "in" is Indonesian-adjacent noise in English text).
     */
    private val INDONESIAN_MARKERS = setOf(
        "yang", "dan", "tidak", "saya", "kamu", "aku", "ini", "itu", "untuk",
        "dengan", "sudah", "bisa", "akan", "atau", "juga", "kalau", "tapi",
        "karena", "jangan", "kita", "mereka", "adalah", "saja", "lagi", "hanya",
        "ke", "di", "pada", "dalam", "sedang", "belum", "harus", "mau", "ingin",
        "banyak", "sekarang", "nanti", "dari", "apa", "siapa", "bagaimana",
        "kenapa", "berapa", "sedikit", "semua", "setiap", "sendiri", "milik",
        "punya", "buat", "biar", "supaya", "agar", "lalu", "kemudian", "jadi",
        "masih", "pernah", "selalu", "sering", "kadang", "dulu", "tolong",
    )

    private val ENGLISH_MARKERS = setOf(
        "the", "and", "is", "are", "was", "were", "you", "your", "to", "of",
        "for", "that", "this", "with", "have", "has", "will", "would", "should",
        "not", "but", "from", "they", "them", "what", "which", "when", "where",
        "how", "there", "here", "been", "being", "can", "could", "about",
    )
}
