package com.opendroid.ai.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.ui.face.rememberReduceMotion
import com.opendroid.ai.ui.theme.LocalOpenDroidColors
import com.opendroid.ai.ui.theme.Montserrat
import kotlinx.coroutines.delay

/**
 * How long the splash holds the app back.
 *
 * The route it leads to is decided off the main thread while this runs (see
 * `OpenDroidNavHost`), so every millisecond here that outlasts that decision is
 * time the user spends looking at a logo for no reason. Short enough to read as
 * a transition rather than a screen.
 */
data class SplashTiming(
    val fadeInMillis: Int,
    val holdMillis: Int,
    val fadeOutMillis: Int,
) {
    val totalMillis: Int get() = fadeInMillis + holdMillis + fadeOutMillis
}

/**
 * A user who has turned animations off gets no fade — but still gets the beat,
 * because cutting straight through would flash the splash for a single frame,
 * which reads as a glitch rather than as a launch.
 */
fun splashTiming(reduceMotion: Boolean): SplashTiming = if (reduceMotion) {
    SplashTiming(fadeInMillis = 0, holdMillis = 280, fadeOutMillis = 0)
} else {
    // Deliberately short. The platform splash has already held for the whole of
    // a two-second cold start by the time this composable draws its first frame,
    // so anything generous here is a second logo screen after the first one -
    // measured on a Pixel 8a, the old 860ms showed the icon, then this, then the
    // app. Now the window colour, the platform splash and this screen are the
    // same black, so it reads as the face arriving on a screen already there.
    SplashTiming(fadeInMillis = 220, holdMillis = 80, fadeOutMillis = 120)
}

@Composable
fun SplashScreen(onNavigateNext: () -> Unit) {
    val colors = LocalOpenDroidColors.current
    val reduceMotion = rememberReduceMotion()
    val timing = remember(reduceMotion) { splashTiming(reduceMotion) }

    val reveal = remember { Animatable(0f) }

    LaunchedEffect(timing) {
        if (timing.fadeInMillis == 0) {
            reveal.snapTo(1f)
        } else {
            reveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(timing.fadeInMillis, easing = FastOutSlowInEasing),
            )
        }
        delay(timing.holdMillis.toLong())
        if (timing.fadeOutMillis > 0) {
            reveal.animateTo(
                targetValue = 0f,
                animationSpec = tween(timing.fadeOutMillis, easing = FastOutSlowInEasing),
            )
        }
        onNavigateNext()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    alpha = reveal.value
                    // A short rise into place, not a slide: enough to give the
                    // fade a direction, small enough that nothing appears to move.
                    translationY = (1f - reveal.value) * 16.dp.toPx()
                },
        ) {
            // The name, and nothing else. The face belongs to the app itself -
            // it is the first thing on the chat screen and the whole of
            // hands-free mode - and putting it here too meant the launch showed
            // it twice before anything happened.
            Text(
                text = "OpenDroid",
                fontFamily = Montserrat,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                letterSpacing = (-0.8).sp,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Your open autonomous Android agent",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.2.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
