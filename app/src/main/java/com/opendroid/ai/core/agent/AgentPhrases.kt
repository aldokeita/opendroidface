// The lines the agent writes itself.
//
// Most of what the user reads comes from the model, in whatever language they
// wrote in. These do not: they are the fast paths and the summaries the loop
// produces without asking anyone, and they were all written in English. Ask in
// Indonesian and the answer came back "Opening that for you" - which reads as
// the assistant not having understood, even when it understood perfectly.
//
// The goal keywords are matched in both languages for the same reason. A plan
// whose goal is "buka whatsapp" is as much a message as one that says "send a
// message", and matching only the English word would file it under "all done".

package com.opendroid.ai.core.agent

object AgentPhrases {

    /** Spoken as an action starts, on the alias fast path. */
    fun preSpeech(action: String, indonesian: Boolean): String = when (action) {
        "TOGGLE_FLASHLIGHT" -> if (indonesian) "Oke, senternya kunyalakan." else "Got it, toggling your flashlight."
        "SET_ALARM" -> if (indonesian) "Baik, alarmnya kupasang." else "Sure, setting that alarm for you."
        "SET_TIMER" -> if (indonesian) "Oke, timernya kumulai." else "Alright, starting a timer."
        "TAKE_SCREENSHOT" -> if (indonesian) "Kuambil tangkapan layarnya." else "Taking a screenshot now."
        "LOCK_SCREEN" -> if (indonesian) "Layarnya kukunci." else "Locking your screen."
        "TOGGLE_WIFI" -> if (indonesian) "Baik, WiFi-nya kuubah." else "Alright, switching your WiFi."
        "TOGGLE_BLUETOOTH" -> if (indonesian) "Oke, Bluetooth-nya kuubah." else "On it, toggling Bluetooth."
        "TOGGLE_DND" -> if (indonesian) "Oke, mode Jangan Ganggu kuubah." else "Got it, changing Do Not Disturb."
        "TOGGLE_HOTSPOT" -> if (indonesian) "Baik, hotspot-nya kuubah." else "Sure, toggling your hotspot."
        "TOGGLE_MOBILE_DATA" -> if (indonesian) "Oke, data selulernya kuubah." else "Alright, switching mobile data."
        "SET_VOLUME" -> if (indonesian) "Oke, volumenya kuatur." else "Got it, adjusting the volume."
        "SET_BRIGHTNESS" -> if (indonesian) "Baik, kecerahannya kuatur." else "Sure, adjusting brightness."
        "OPEN_APP" -> if (indonesian) "Kubukakan, ya." else "Opening that for you."
        "ANALYZE_SCREENSHOT" -> if (indonesian) "Kulihat dulu layarmu." else "Let me take a look at your screen."
        "READ_AND_REMEMBER_SCREEN" ->
            if (indonesian) "Kubaca layarnya dan kusimpan yang penting."
            else "Reading your screen and saving the important details."
        "READ_NOTES" -> if (indonesian) "Kucari catatanmu." else "Let me look up your notes."
        "RECALL_MEMORY" -> if (indonesian) "Kucari di ingatanku." else "Searching your saved memories."
        "ADD_NOTE" -> if (indonesian) "Catatannya kusimpan." else "Saving that note for you."
        "SET_RINGER_MODE" -> if (indonesian) "Mode deringnya kuubah." else "Changing your ringer mode."
        "PLAY_MUSIC" -> if (indonesian) "Kuputarkan, ya." else "Let me play that for you."
        "MAKE_CALL" -> if (indonesian) "Kutelepon sekarang." else "Calling now."
        else -> {
            val readable = action.lowercase().replace("_", " ")
            if (indonesian) "Siap, kukerjakan: $readable." else "On it! Let me $readable."
        }
    }

    /** Said when a plan finished and produced nothing worth quoting. */
    fun goalDone(goal: String, indonesian: Boolean): String = when (topicOf(goal)) {
        Topic.ALARM -> if (indonesian) "Beres, alarmnya sudah siap." else "All set! Your alarm is ready."
        Topic.FLASHLIGHT -> if (indonesian) "Selesai, senternya sudah kuubah." else "Done! Flashlight's been toggled."
        Topic.WIFI -> if (indonesian) "WiFi-nya sudah kuubah." else "WiFi's been updated."
        Topic.BLUETOOTH -> if (indonesian) "Bluetooth-nya sudah kuubah." else "Bluetooth's been switched."
        Topic.VOLUME -> if (indonesian) "Volumenya sudah kuatur." else "Volume's adjusted."
        Topic.BRIGHTNESS -> if (indonesian) "Kecerahannya sudah kuatur." else "Brightness updated."
        Topic.SCREENSHOT -> if (indonesian) "Tangkapan layarnya sudah kuambil." else "Screenshot taken!"
        Topic.TIMER -> if (indonesian) "Timernya sudah jalan." else "Timer's set and running."
        Topic.OPEN -> if (indonesian) "Sudah kubuka." else "Done, it should be open now."
        Topic.CALL -> if (indonesian) "Kutelepon sekarang." else "Calling now."
        Topic.MESSAGE -> if (indonesian) "Pesannya sudah terkirim." else "Message sent!"
        Topic.OTHER -> if (indonesian) "Sudah selesai." else "All done!"
    }

    /** Said when a plan failed. The technical reason stays in the log. */
    fun failure(goal: String, indonesian: Boolean): String = when (topicOf(goal)) {
        Topic.ALARM ->
            if (indonesian) "Maaf, alarmnya tidak jadi terpasang. Coba cek aplikasi Jam?"
            else "Sorry, I couldn't set that alarm. Maybe check your Clock app?"
        Topic.FLASHLIGHT ->
            if (indonesian) "Senternya tidak mau menyala. Coba lagi?"
            else "Hmm, the flashlight didn't work. Try again?"
        Topic.CALL ->
            if (indonesian) "Teleponnya tidak berhasil. Mau kucoba lagi?"
            else "I wasn't able to make that call. Want to try again?"
        Topic.MESSAGE ->
            if (indonesian) "Pesannya tidak terkirim. Mau kucoba lagi?"
            else "The message didn't go through. Want me to retry?"
        Topic.WIFI, Topic.BLUETOOTH ->
            if (indonesian) "Setelan itu tidak bisa kuubah. Mungkin harus lewat setelan HP."
            else "Couldn't change that setting. You might need to do it manually."
        else ->
            if (indonesian) "Maaf, itu tidak berhasil. Mau kucoba lagi?"
            else "Sorry, that didn't work out. Want me to try again?"
    }

    /** Leads an auto-approved plan, so nobody waits for an approval prompt. */
    fun runningPlan(goal: String, indonesian: Boolean): String =
        if (indonesian) "Kukerjakan: $goal" else "Running: $goal"

    /**
     * Said when a plan needs approval.
     *
     * It does not repeat the request. Reading the user's own command back to
     * them before asking for a yes wastes the seconds they need to answer, and
     * they already know what they asked for - the plan is on screen.
     */
    fun approvalPrompt(indonesian: Boolean): String =
        if (indonesian) "Perlu persetujuanmu." else "This needs your approval."

    /**
     * Translates a step's own result line, when it recognises one.
     *
     * The executors in `actions/` report what they did in English - "WhatsApp
     * is open!", "Screenshot taken!" - and the plan summary quotes them
     * verbatim. In an Indonesian conversation that lands as the assistant
     * switching languages mid-answer.
     *
     * Only status lines are translated, and only ones matched exactly or by a
     * narrow pattern. Anything unrecognised is returned unchanged rather than
     * dropped: a result that carries real content - a count, a name, an answer
     * - is worth more in the wrong language than not at all.
     */
    fun localizeStatus(result: String, indonesian: Boolean): String {
        if (!indonesian) return result
        val trimmed = result.trim()
        EXACT_STATUS[trimmed]?.let { return it }

        // "<something> is open!" is how every app-launching action reports, and
        // the something is a proper noun that survives translation untouched.
        OPENED.matchEntire(trimmed)?.let { match ->
            return "${match.groupValues[1]} sudah kubuka."
        }
        return trimmed
    }

    private val OPENED = Regex("""(.+?) is open[!.]?""", RegexOption.IGNORE_CASE)

    private val EXACT_STATUS: Map<String, String> = mapOf(
        "Your email is open!" to "Emailmu sudah kubuka.",
        "Browser is open!" to "Peramban sudah kubuka.",
        "Screenshot taken!" to "Tangkapan layarnya sudah kuambil.",
        "Screen locked!" to "Layarnya sudah kukunci.",
        "Message sent!" to "Pesannya sudah terkirim.",
        "Calling now." to "Kutelepon sekarang.",
        "Done!" to "Selesai.",
        "All done!" to "Sudah selesai.",
    )

    private enum class Topic {
        ALARM, FLASHLIGHT, WIFI, BLUETOOTH, VOLUME, BRIGHTNESS,
        SCREENSHOT, TIMER, OPEN, CALL, MESSAGE, OTHER
    }

    private fun topicOf(goal: String): Topic {
        val lower = goal.lowercase()
        fun has(vararg words: String) = words.any { lower.contains(it) }
        return when {
            has("alarm") -> Topic.ALARM
            has("flash", "torch", "senter") -> Topic.FLASHLIGHT
            has("wifi", "wi-fi") -> Topic.WIFI
            has("bluetooth") -> Topic.BLUETOOTH
            has("volume", "suara", "keras") -> Topic.VOLUME
            has("brightness", "cerah", "terang") -> Topic.BRIGHTNESS
            has("screenshot", "tangkapan layar", "ss layar") -> Topic.SCREENSHOT
            has("timer", "penghitung waktu") -> Topic.TIMER
            has("open", "buka") -> Topic.OPEN
            has("call", "telepon", "panggil", "hubungi") -> Topic.CALL
            has("message", "whatsapp", "pesan", "kirim", "sms") -> Topic.MESSAGE
            else -> Topic.OTHER
        }
    }
}
