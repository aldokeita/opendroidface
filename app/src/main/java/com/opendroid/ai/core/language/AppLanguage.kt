// The language the assistant answers in.
//
// This started as guesswork: the loop read the user's words and decided from
// them. That works for a full sentence and is a coin toss for "buka whatsapp",
// and a coin toss is a bad thing to have between a person and their assistant -
// the same request could be answered in either language depending on which
// words happened to be in it.
//
// So it is a setting. Following the device stays the default, because a phone
// set to Indonesian should not have to be told twice, and that path keeps the
// text-based guess as its last resort for a device set to neither.

package com.opendroid.ai.core.language

import android.content.Context
import androidx.core.content.edit
import com.opendroid.ai.core.voice.SpokenLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLanguage(val id: String, val label: String) {
    /** Follow the device, then the words themselves. */
    SYSTEM("system", "Follow device"),
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English");

    companion object {
        fun fromId(id: String?): AppLanguage =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Whether this turn should be Indonesian.
 *
 * @param text the user's own words, consulted only when nothing has been
 * chosen and the device itself is not Indonesian.
 */
fun AppLanguage.wantsIndonesian(
    text: String,
    deviceLocale: Locale = Locale.getDefault(),
): Boolean = when (this) {
    AppLanguage.INDONESIAN -> true
    AppLanguage.ENGLISH -> false
    AppLanguage.SYSTEM ->
        deviceLocale.language == SpokenLanguage.INDONESIAN.language ||
            SpokenLanguage.looksIndonesian(text)
}

/** The language tag for speech, or null to let the utterance decide. */
fun AppLanguage.speechTag(): String? = when (this) {
    AppLanguage.INDONESIAN -> "id-ID"
    AppLanguage.ENGLISH -> "en-US"
    AppLanguage.SYSTEM -> null
}

@Singleton
class AppLanguageStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _language = MutableStateFlow(AppLanguage.fromId(prefs.getString(KEY, null)))

    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun select(language: AppLanguage) {
        if (_language.value == language) return
        _language.value = language
        prefs.edit { putString(KEY, language.id) }
    }

    private companion object {
        const val PREFS = "opendroid_app_language"
        const val KEY = "language"
    }
}
