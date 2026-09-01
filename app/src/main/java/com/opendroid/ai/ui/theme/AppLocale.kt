// Makes the whole interface follow the app's own language setting.
//
// Not the device's. Someone whose phone is in English may still want the
// assistant, its buttons and its error messages in Indonesian - that is exactly
// the case this app is used in - and asking them to change their entire phone
// to get it would be a strange price.
//
// So the setting the assistant already reads for its own replies also selects
// the resources every screen resolves. `stringResource` reads the resources of
// LocalContext, and LocalConfiguration is what Compose invalidates on, so both
// are replaced together: change the setting and the interface changes under
// your finger, with no restart.

package com.opendroid.ai.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.opendroid.ai.core.language.AppLanguage
import com.opendroid.ai.ui.face.rememberAppLanguageStore
import java.util.Locale

// Lint reads a runtime locale change as an App Bundle hazard: an app that
// switches language on the fly needs every translation present, and Play's
// per-language splits would deliver only one. The bundle block in
// app/build.gradle disables that splitting, and this app is distributed as an
// APK from GitHub Releases in any case - neither fact is visible from here.
@Suppress("AppBundleLocaleChanges")
@Composable
fun ProvideAppLocale(content: @Composable () -> Unit) {
    val language by rememberAppLanguageStore().language.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val locale = language.uiLocale()
    if (locale == null) {
        // Following the device: leave Android's own resolution alone rather
        // than re-deriving it and getting a corner case wrong.
        content()
        return
    }

    val localized = remember(context, locale, configuration) {
        val updated = Configuration(configuration).apply { setLocale(locale) }
        // A ContextWrapper around the original, NOT the bare context that
        // createConfigurationContext hands back.
        //
        // That bare context is a ContextImpl with no Activity behind it, and
        // hiltViewModel() finds its Activity by unwrapping the ContextWrapper
        // chain - so providing it crashed every screen that asks for a view
        // model, which is all of them:
        //
        //   Expected an activity context for creating a HiltViewModelFactory
        //
        // Wrapping keeps the Activity reachable and swaps only the resources,
        // which is the only part that had to change.
        val localizedResources = context.createConfigurationContext(updated).resources
        val wrapper = object : ContextWrapper(context) {
            override fun getResources(): Resources = localizedResources
        }
        wrapper to updated
    }

    CompositionLocalProvider(
        LocalContext provides localized.first,
        LocalConfiguration provides localized.second,
        content = content,
    )
}

/** The locale this setting selects, or null while it follows the device. */
fun AppLanguage.uiLocale(): Locale? = when (this) {
    AppLanguage.INDONESIAN -> Locale.forLanguageTag("id-ID")
    AppLanguage.ENGLISH -> Locale.forLanguageTag("en-US")
    AppLanguage.SYSTEM -> null
}

/**
 * The same choice, for code that has a [Context] but no composition - the
 * foreground service and the agent loop, whose messages reach the same person.
 */
@Suppress("AppBundleLocaleChanges")
fun Context.localizedFor(language: AppLanguage): Context {
    val locale = language.uiLocale() ?: return this
    val configuration = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(configuration)
}


