package com.opendroid.ai.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Applies edge-to-edge using the app's selected theme rather than the device's system theme.
 *
 * AndroidX uses the system UI mode to select the API 26-28 navigation-bar scrim by default.
 * OpenDroid supports an in-app theme toggle, so use the selected palette instead. On API 29+
 * [SystemBarStyle.auto] keeps both bars transparent, preserving edge-to-edge behavior.
 */
internal fun ComponentActivity.enableOpenDroidEdgeToEdge(isDarkTheme: Boolean) {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(
            lightScrim = Color.TRANSPARENT,
            darkScrim = Color.TRANSPARENT,
            detectDarkMode = { isDarkTheme }
        ),
        navigationBarStyle = SystemBarStyle.auto(
            lightScrim = LightPalette.background.toArgb(),
            darkScrim = DarkPalette.background.toArgb(),
            detectDarkMode = { isDarkTheme }
        )
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.accentNeonGreen,
    secondary = DarkPalette.accentPurple,
    tertiary = DarkPalette.accentCyan,
    background = DarkPalette.background,
    surface = DarkPalette.surface,
    onPrimary = DarkPalette.background,
    onSecondary = DarkPalette.textPrimary,
    onBackground = DarkPalette.textPrimary,
    onSurface = DarkPalette.textPrimary,
    error = DarkPalette.accentRed
)

private val LightColorScheme = lightColorScheme(
    primary = LightPalette.accentNeonGreen,
    secondary = LightPalette.accentPurple,
    tertiary = LightPalette.accentCyan,
    background = LightPalette.background,
    surface = LightPalette.surface,
    onPrimary = LightPalette.surface,
    onSecondary = LightPalette.textPrimary,
    onBackground = LightPalette.textPrimary,
    onSurface = LightPalette.textPrimary,
    error = LightPalette.accentRed
)

@Composable
fun OpenDroidTheme(
    isDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // The accent is applied here, once, so every screen picks it up by reading
    // the palette it already reads. Doing it per screen would have meant every
    // screen having an opinion about it.
    val accentId by rememberAccentStore().accentId.collectAsState()
    val accent = accentOptionFor(accentId)
    val palette = (if (isDarkTheme) DarkPalette else LightPalette).withAccent(accent)
    val accentColor = if (isDarkTheme) accent.dark else accent.light
    val colorScheme = (if (isDarkTheme) DarkColorScheme else LightColorScheme)
        .copy(primary = accentColor)

    val view = LocalView.current
    val activity = view.context as? ComponentActivity
    if (!view.isInEditMode && activity != null) {
        SideEffect {
            activity.enableOpenDroidEdgeToEdge(isDarkTheme)
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, view)
            // Light status bar icons for dark theme, dark icons for light theme
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    // Wrapping the whole theme means every screen inside it resolves strings in
    // the chosen language, including ones that have not been converted yet -
    // they simply keep resolving to English until they are.
    ProvideAppLocale {
    CompositionLocalProvider(LocalOpenDroidColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
        ) {
            // Most screens call Text with a size and a colour but no family, which
            // would leave them on the platform sans-serif no matter what the type
            // scale says. Providing the family as the ambient text style covers
            // them all: an explicit fontSize still wins, the family comes from here.
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = PlusJakartaSans),
                content = content,
            )
        }
    }
    }
}
