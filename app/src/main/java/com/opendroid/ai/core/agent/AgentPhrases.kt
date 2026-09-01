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
     * Whether a step's result is machinery rather than an answer.
     *
     * The summary quotes what each step reported, and the interface primitives
     * report to a log, not to a person: "Waited 2000ms", "Tapped on
     * 'Sayangku'!". Read aloud that is the assistant narrating its own hands.
     * Nobody asked how it did it.
     *
     * Opening an app counts as plumbing only inside a longer plan, where it is
     * a means to somewhere else. On its own it IS the request.
     */
    fun isPlumbing(action: String, stepsInPlan: Int): Boolean = when (action) {
        "WAIT", "CLICK_TEXT", "CLICK_ID", "CLICK_COORDINATES",
        "TYPE_TEXT", "TYPE_ID", "SCROLL", "PRESS_ENTER" -> true
        "OPEN_APP" -> stepsInPlan > 1
        else -> false
    }

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

    /**
     * What the executors in `actions/` report, in Indonesian.
     *
     * Translated here rather than at the two hundred call sites, and the choice
     * is deliberate: those files are upstream's, this one is ours. Editing them
     * would put a conflict in every future `git merge upstream/main` for the
     * life of the fork, in exchange for the same words on screen.
     *
     * The cost is that a line upstream rewrites stops being recognised and
     * falls back to English rather than breaking. That is the right way round.
     */
    private val EXACT_STATUS: Map<String, String> = mapOf(
        // ── Already covered by the generic "<app> is open!" pattern ──────
        "Your email is open!" to "Emailmu sudah kubuka.",
        "Browser is open!" to "Peramban sudah kubuka.",
        "Telegram is open!" to "Telegram sudah kubuka.",

        // ── Success ─────────────────────────────────────────────────────
        "Screenshot taken!" to "Tangkapan layarnya sudah kuambil.",
        "Screen locked!" to "Layarnya sudah kukunci.",
        "Message sent!" to "Pesannya sudah terkirim.",
        "Email sent successfully." to "Emailnya sudah terkirim.",
        "Calling now." to "Kutelepon sekarang.",
        "Done!" to "Selesai.",
        "All done!" to "Sudah selesai.",
        "Photo saved!" to "Fotonya sudah tersimpan.",
        "Camera is ready — snap away!" to "Kameranya siap, silakan jepret.",
        "Camera is ready for video!" to "Kameranya siap merekam.",
        "Music paused!" to "Musiknya kujeda.",
        "Music resumed!" to "Musiknya kulanjutkan.",
        "Skipped to the next song!" to "Lanjut ke lagu berikutnya.",
        "Going back to the previous song!" to "Balik ke lagu sebelumnya.",
        "Clipboard cleared!" to "Papan klip sudah kubersihkan.",
        "Clipboard is empty." to "Papan klipnya kosong.",
        "Here are your messages!" to "Ini pesan-pesanmu.",
        "Here's your calendar for today." to "Ini jadwalmu hari ini.",
        "Here's your week at a glance." to "Ini gambaran satu minggumu.",
        "Let me check that for you!" to "Kucek dulu, ya.",
        "Opening that page for you!" to "Kubukakan halamannya.",
        "Opening Google Translate for you!" to "Kubukakan Google Translate.",
        "Opened Chrome in incognito mode!" to "Chrome kubuka dalam mode samaran.",
        "Opened Telegram Web in browser." to "Telegram Web kubuka di peramban.",
        "Opened the wallpaper picker for you." to "Pemilih wallpaper sudah kubuka.",
        "No notifications found." to "Tidak ada notifikasi.",
        "No matching notifications found." to "Tidak ada notifikasi yang cocok.",
        "No knowledge graph entries found matching your query." to
            "Tidak ada catatan yang cocok dengan pencarianmu.",
        "That page isn't on this phone, so I opened Settings." to
            "Halaman itu tidak ada di HP ini, jadi kubuka Setelan.",
        "Browser is open — you can switch to private/incognito mode manually." to
            "Peramban sudah kubuka. Mode samarannya nyalakan sendiri, ya.",
        "App settings are open — find your browser and clear its data." to
            "Setelan aplikasi sudah kubuka. Cari peramban kamu lalu hapus datanya.",

        // ── Things that are not installed ───────────────────────────────
        "Maps app isn't installed, but I opened it in your browser!" to
            "Aplikasi peta tidak terpasang, jadi kubuka lewat peramban.",
        "Uber isn't installed, but I opened the website for you!" to
            "Uber tidak terpasang, jadi kubuka situsnya.",
        "Amazon isn't installed, but I opened the search in your browser!" to
            "Amazon tidak terpasang, jadi pencariannya kubuka di peramban.",
        "Flipkart isn't installed, but I opened it in your browser!" to
            "Flipkart tidak terpasang, jadi kubuka lewat peramban.",
        "Ola isn't installed — I'll take you to the Play Store to get it!" to
            "Ola tidak terpasang. Kuantar ke Play Store buat memasangnya.",
        "Google Home isn't installed, so I searched for it instead." to
            "Google Home tidak terpasang, jadi kucarikan saja.",

        // ── Failures the user is meant to read ──────────────────────────
        "Couldn't do that right now. Try again?" to "Belum bisa sekarang. Coba lagi?",
        "Couldn't toggle WiFi." to "WiFi-nya tidak bisa kuubah.",
        "Couldn't toggle mobile data." to "Data selulernya tidak bisa kuubah.",
        "Couldn't toggle hotspot." to "Hotspot-nya tidak bisa kuubah.",
        "Couldn't toggle the flashlight." to "Senternya tidak bisa kuubah.",
        "Couldn't change Do Not Disturb." to "Mode Jangan Ganggu tidak bisa kuubah.",
        "Couldn't change the brightness right now. Please try again." to
            "Kecerahannya belum bisa kuubah. Coba lagi ya.",
        "Couldn't change the volume right now. Please try again." to
            "Volumenya belum bisa kuubah. Coba lagi ya.",
        "Couldn't change the screen timeout right now. Please try again." to
            "Waktu mati layarnya belum bisa kuubah. Coba lagi ya.",
        "Couldn't change the ringer mode right now." to "Mode deringnya belum bisa kuubah.",
        "Couldn't change the music volume." to "Volume musiknya tidak bisa kuubah.",
        "Couldn't pause the music." to "Musiknya tidak bisa kujeda.",
        "Couldn't resume the music." to "Musiknya tidak bisa kulanjutkan.",
        "Couldn't skip the track." to "Lagunya tidak bisa kulewati.",
        "Couldn't play that right now. Try again?" to "Belum bisa kuputar. Coba lagi?",
        "Couldn't open the camera." to "Kameranya tidak bisa kubuka.",
        "Couldn't open the camera app." to "Aplikasi kameranya tidak bisa kubuka.",
        "Couldn't open the video camera." to "Kamera videonya tidak bisa kubuka.",
        "Couldn't open the browser." to "Peramban tidak bisa kubuka.",
        "Couldn't open that link right now." to "Tautannya belum bisa kubuka.",
        "Couldn't open that URL." to "Alamat itu tidak bisa kubuka.",
        "Couldn't open the settings." to "Setelannya tidak bisa kubuka.",
        "Couldn't open the email app." to "Aplikasi emailnya tidak bisa kubuka.",
        "Couldn't open the email app. Is one installed?" to
            "Aplikasi emailnya tidak bisa kubuka. Sudah terpasang belum?",
        "Couldn't open WhatsApp. Is it installed?" to
            "WhatsApp tidak bisa kubuka. Sudah terpasang belum?",
        "Couldn't open Telegram right now." to "Telegram belum bisa kubuka.",
        "Couldn't open the messaging app." to "Aplikasi pesannya tidak bisa kubuka.",
        "Couldn't open messaging. Try again?" to "Aplikasi pesannya tidak bisa kubuka. Coba lagi?",
        "Couldn't open your messages right now." to "Pesanmu belum bisa kubuka.",
        "Couldn't open your calendar." to "Kalendermu tidak bisa kubuka.",
        "Couldn't open YouTube right now." to "YouTube belum bisa kubuka.",
        "Couldn't open the wallpaper picker." to "Pemilih wallpaper tidak bisa kubuka.",
        "Couldn't read the clipboard." to "Papan klipnya tidak bisa kubaca.",
        "Couldn't copy to clipboard." to "Tidak bisa menyalin ke papan klip.",
        "Couldn't clear the clipboard." to "Papan klipnya tidak bisa kubersihkan.",
        "Couldn't read your contacts." to "Kontakmu tidak bisa kubaca.",
        "Couldn't read your notes right now." to "Catatanmu belum bisa kubaca.",
        "Couldn't save that note." to "Catatannya tidak bisa kusimpan.",
        "Couldn't read notifications right now." to "Notifikasinya belum bisa kubaca.",
        "Couldn't check the weather right now. Please check your internet connection." to
            "Cuacanya belum bisa kucek. Coba periksa koneksi internetmu.",
        "Couldn't fetch the news right now." to "Beritanya belum bisa kuambil.",
        "Couldn't search right now. Try again?" to "Belum bisa mencari sekarang. Coba lagi?",
        "Couldn't look that up right now." to "Belum bisa kucari sekarang.",
        "Couldn't check that right now." to "Belum bisa kucek sekarang.",
        "Couldn't get directions right now. Try again?" to "Rutenya belum bisa kuambil. Coba lagi?",
        "Couldn't check traffic right now." to "Kondisi lalu lintasnya belum bisa kucek.",
        "Couldn't start the timer." to "Timernya tidak bisa kumulai.",
        "Couldn't set up that reminder." to "Pengingatnya tidak bisa kupasang.",
        "Couldn't analyze the screen right now." to "Layarnya belum bisa kubaca.",
        "Couldn't get system info right now." to "Info sistemnya belum bisa kuambil.",
        "Couldn't go back." to "Tidak bisa kembali.",
        "Couldn't list the apps right now." to "Daftar aplikasinya belum bisa kuambil.",
        "Couldn't list your macros." to "Daftar makromu tidak bisa kuambil.",
        "Couldn't create that macro." to "Makronya tidak bisa kubuat.",
        "Couldn't schedule that macro." to "Makronya tidak bisa kujadwalkan.",
        "Couldn't retrieve memory right now." to "Ingatannya belum bisa kuambil.",
        "Couldn't update auto-reply settings." to "Setelan balas otomatisnya tidak bisa kuubah.",
        "This device doesn't have a camera." to "HP ini tidak punya kamera.",
        "No cameras available on this device" to "Tidak ada kamera di HP ini.",
        "No camera with flashlight support was found." to "Tidak ada kamera yang punya senter.",
        "Device doesn't have Bluetooth hardware." to "HP ini tidak punya Bluetooth.",
        "Taking a screenshot requires Android 9 or newer." to
            "Tangkapan layar butuh Android 9 atau lebih baru.",
        "Locking the screen requires Android 9 or newer." to
            "Mengunci layar butuh Android 9 atau lebih baru.",
        "I need Do Not Disturb access to change the ringer mode. Please grant it in Settings." to
            "Aku butuh izin Jangan Ganggu untuk mengubah mode dering. Berikan lewat Setelan, ya.",
        "I tapped send but the message is still in the input field. Please send it manually." to
            "Tombol kirimnya sudah kutekan, tapi pesannya masih di kolom ketik. Kirim manual ya.",
        "Note content is empty." to "Isi catatannya kosong.",
        "No text provided to copy" to "Tidak ada teks untuk disalin.",
        "No URL provided" to "Alamatnya belum diisi.",
        "No screen timeout was given." to "Waktu mati layarnya belum disebutkan.",
        "No text size was given." to "Ukuran teksnya belum disebutkan.",
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
