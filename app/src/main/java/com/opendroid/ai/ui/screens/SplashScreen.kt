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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import com.opendroid.ai.R
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
/** How wide the accent hairline grows. Roughly the width of the wordmark. */
private val RULE_WIDTH = 96.dp

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
    SplashTiming(fadeInMillis = 0, holdMillis = 1_100, fadeOutMillis = 0)
} else {
    // Paced to be looked at rather than caught. The three staged parts below run
    // over the fade-in, and the hold after them is the part that decides whether
    // the screen reads as a moment or as a frame that flicked past - at 480ms it
    // was still the latter.
    SplashTiming(fadeInMillis = 520, holdMillis = 900, fadeOutMillis = 320)
}

@Composable
fun SplashScreen(onNavigateNext: () -> Unit) {
    val colors = LocalOpenDroidColors.current
    val reduceMotion = rememberReduceMotion()
    val timing = remember(reduceMotion) { splashTiming(reduceMotion) }

    // Three parts, staged rather than simultaneous. Everything appearing at once
    // is a picture being switched on; one thing after another is a sequence, and
    // a sequence is the only thing a second of screen time can be spent on
    // without feeling like a wait.
    val name = remember { Animatable(0f) }
    val rule = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    // Kept separate from the three so the exit dims whatever state they are in,
    // rather than rewinding the entrance.
    val exit = remember { Animatable(1f) }

    LaunchedEffect(timing) {
        if (timing.fadeInMillis == 0) {
            name.snapTo(1f)
            rule.snapTo(1f)
            tagline.snapTo(1f)
        } else {
            launch {
                name.animateTo(1f, tween(timing.fadeInMillis, easing = FastOutSlowInEasing))
            }
            launch {
                // The rule starts while the name is still arriving, so it reads as
                // being drawn under it rather than as a third thing appearing.
                delay(220)
                rule.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
            }
            launch {
                delay(420)
                tagline.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            }
        }
        delay((timing.fadeInMillis + timing.holdMillis).toLong())
        if (timing.fadeOutMillis > 0) {
            exit.animateTo(0f, tween(timing.fadeOutMillis, easing = FastOutSlowInEasing))
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
                .graphicsLayer { alpha = exit.value },
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
                modifier = Modifier.graphicsLayer {
                    alpha = name.value
                    // A short rise into place, not a slide: enough to give the
                    // fade a direction, small enough that nothing appears to move.
                    translationY = (1f - name.value) * 18.dp.toPx()
                    // And a fraction of scale with it, so the name settles rather
                    // than lands.
                    val s = 0.94f + 0.06f * name.value
                    scaleX = s
                    scaleY = s
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            // A hairline drawn out from the centre. It is the one moving thing on
            // the screen, and it gives the beat between the name and the line
            // under it something to be filled with.
            Box(
                modifier = Modifier
                    .width(RULE_WIDTH * rule.value)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.accentNeonGreen)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.2.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = tagline.value
                    translationY = (1f - tagline.value) * 8.dp.toPx()
                },
            )
        }
    }
}

