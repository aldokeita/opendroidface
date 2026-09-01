// What a text-to-speech voice should actually be handed.
//
// Models write for a screen even when told not to: asterisks around emphasis,
// backticks around names, bullets, emoji, the occasional URL. A TTS engine has
// no idea any of that is formatting, so it pronounces it - "bintang bintang
// selesai bintang bintang" - and the punctuation ends up louder than the
// sentence.
//
// The prompt asks the model to keep speech plain, and this cleans up what
// arrives anyway. It runs on the way to the speaker only: what is stored and
// shown keeps the model's own formatting.

package com.opendroid.ai.core.voice

object SpeechText {

    /** Strips formatting a voice would pronounce, and returns what to say. */
    fun forSpeech(text: String): String {
        var spoken = text

        // Fenced and inline code: the fence markers are noise, the code inside
        // is usually a name worth saying.
        spoken = FENCE.replace(spoken, " ")
        spoken = INLINE_CODE.replace(spoken, "$1")

        // Links become their label. A bare URL is read character by character,
        // which is unbearable and never useful out loud.
        spoken = MARKDOWN_LINK.replace(spoken, "$1")
        spoken = BARE_URL.replace(spoken, " ")

        // Emphasis. Longest marker first, so ***both*** does not leave a stray
        // asterisk behind.
        spoken = BOLD_ITALIC.replace(spoken, "$1")
        spoken = BOLD.replace(spoken, "$1")
        spoken = ITALIC.replace(spoken, "$1")
        spoken = UNDERSCORE_EMPHASIS.replace(spoken, "$1")
        spoken = STRIKETHROUGH.replace(spoken, "$1")

        // Line-leading structure: headings, bullets, quotes, numbered items.
        // A list read aloud is a sentence, so the markers go and the line ends
        // become pauses.
        spoken = HEADING.replace(spoken, "")
        spoken = BULLET.replace(spoken, "")
        spoken = QUOTE.replace(spoken, "")
        spoken = RULE.replace(spoken, " ")

        // Emoji are read out by name on some engines and skipped on others;
        // either way they are decoration that was never meant to be heard.
        spoken = EMOJI.replace(spoken, " ")

        // Anything left over that only ever marked something up.
        spoken = LEFTOVER_MARKS.replace(spoken, "")

        return spoken.replace(WHITESPACE, " ").trim()
    }

    private val FENCE = Regex("```[\\s\\S]*?```")
    private val INLINE_CODE = Regex("`([^`]*)`")
    private val MARKDOWN_LINK = Regex("""\[([^]]*)]\([^)]*\)""")
    private val BARE_URL = Regex("""\bhttps?://\S+""")

    private val BOLD_ITALIC = Regex("""\*\*\*(.+?)\*\*\*""", RegexOption.DOT_MATCHES_ALL)
    private val BOLD = Regex("""\*\*(.+?)\*\*""", RegexOption.DOT_MATCHES_ALL)
    private val ITALIC = Regex("""\*(.+?)\*""", RegexOption.DOT_MATCHES_ALL)
    private val UNDERSCORE_EMPHASIS = Regex("""(?<![A-Za-z0-9_])_{1,2}(.+?)_{1,2}(?![A-Za-z0-9_])""")
    private val STRIKETHROUGH = Regex("""~~(.+?)~~""")

    private val HEADING = Regex("""(?m)^\s{0,3}#{1,6}\s*""")
    private val BULLET = Regex("""(?m)^\s{0,6}(?:[-*+•]|\d{1,2}[.)])\s+""")
    private val QUOTE = Regex("""(?m)^\s{0,3}>\s?""")
    private val RULE = Regex("""(?m)^\s*(?:-{3,}|\*{3,}|_{3,})\s*$""")

    /**
     * Pictographs, symbols, flags and the variation selectors that follow them.
     * Deliberately not `\p{So}`, which would also take mathematical and
     * currency symbols out of sentences that need them.
     */
    private val EMOJI = Regex(
        "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}\\x{2190}-\\x{21FF}\\x{2B00}-\\x{2BFF}]+"
    )

    private val LEFTOVER_MARKS = Regex("""[*_`~#]""")
    private val WHITESPACE = Regex("""\s+""")
}
