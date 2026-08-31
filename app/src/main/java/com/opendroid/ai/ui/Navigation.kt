package com.opendroid.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.opendroid.ai.ui.screens.*
import com.opendroid.ai.ui.theme.*
import com.opendroid.ai.ui.viewmodel.*

/**
 * Route names of the top-level navigation graph.
 *
 * Kept next to [navigateAfterSplash] and [navigateAfterOnboarding] so the back-stack
 * rules of the entry flow can be exercised without composing the screens themselves.
 */
object OpenDroidRoutes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val BENCHMARK = "benchmark"
    const val PRIVACY_POLICY = "privacy_policy"
    const val TERMS_OF_USE = "terms_of_use"
    const val HELP_CENTER = "help_center"
    const val LICENSE = "license"
    const val ABOUT = "about"
    const val AUTO_REPLY_SETTINGS = "auto_reply_settings"
    const val NOTIFICATION_HISTORY = "notification_history"
    const val PERMISSIONS = "permissions"
    const val CRASH_LOG = "crash_log"
    const val ROUTINES = "routines"

    // Plan, Macros and Logs used to be tabs. They are places you visit to check
    // on something that already happened, not places you work, so they live one
    // level down under Settings and the bar is left with the three tabs a person
    // actually moves between.
    const val PLAN = "plan"
    const val MACROS = "macros"
    const val LOGS = "logs"
}

/**
 * Leaves the splash screen for onboarding or the main dashboard, dropping splash from the
 * back stack so system back from the first real screen exits the app instead of replaying it.
 */
fun NavHostController.navigateAfterSplash(isOnboardingCompleted: Boolean) {
    val destination = if (isOnboardingCompleted) OpenDroidRoutes.MAIN else OpenDroidRoutes.ONBOARDING
    navigate(destination) {
        popUpTo(OpenDroidRoutes.SPLASH) { inclusive = true }
    }
}

/** Enters the dashboard after onboarding, so back never returns to the completed flow. */
fun NavHostController.navigateAfterOnboarding() {
    navigate(OpenDroidRoutes.MAIN) {
        popUpTo(OpenDroidRoutes.ONBOARDING) { inclusive = true }
    }
}

@Composable
fun OpenDroidNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = OpenDroidRoutes.SPLASH,
        modifier = Modifier.fillMaxSize().background(AppTheme.colors.background)
    ) {
        composable(OpenDroidRoutes.SPLASH) {
            val startupViewModel: StartupViewModel = hiltViewModel()
            val startDestination by startupViewModel.startDestination.collectAsState()
            var splashFinished by remember { mutableStateOf(false) }

            SplashScreen(onNavigateNext = { splashFinished = true })

            // The destination is decided off the main thread, so wait for both the animation and
            // the decrypted profile check rather than guessing a route.
            LaunchedEffect(splashFinished, startDestination) {
                val destination = startDestination
                if (splashFinished && destination != null) {
                    navController.navigateAfterSplash(
                        isOnboardingCompleted = destination == StartDestination.MAIN
                    )
                }
            }
        }

        composable(OpenDroidRoutes.ONBOARDING) {
            OnboardingScreen(
                onFinished = { navController.navigateAfterOnboarding() }
            )
        }

        composable(OpenDroidRoutes.MAIN) {
            MainDashboard(
                onNavigateToBenchmark = {
                    navController.navigate(OpenDroidRoutes.BENCHMARK)
                },
                onNavigateToPrivacyPolicy = {
                    navController.navigate(OpenDroidRoutes.PRIVACY_POLICY)
                },
                onNavigateToTermsOfUse = {
                    navController.navigate(OpenDroidRoutes.TERMS_OF_USE)
                },
                onNavigateToHelpCenter = {
                    navController.navigate(OpenDroidRoutes.HELP_CENTER)
                },
                onNavigateToLicense = {
                    navController.navigate(OpenDroidRoutes.LICENSE)
                },
                onNavigateToAbout = {
                    navController.navigate(OpenDroidRoutes.ABOUT)
                },
                onNavigateToAutoReply = {
                    navController.navigate(OpenDroidRoutes.AUTO_REPLY_SETTINGS)
                },
                onNavigateToNotificationHistory = {
                    navController.navigate(OpenDroidRoutes.NOTIFICATION_HISTORY)
                },
                onNavigateToPermissions = {
                    navController.navigate(OpenDroidRoutes.PERMISSIONS)
                },
                onNavigateToCrashLog = {
                    navController.navigate(OpenDroidRoutes.CRASH_LOG)
                },
                onNavigateToRoutines = {
                    navController.navigate(OpenDroidRoutes.ROUTINES)
                },
                onNavigateToPlan = {
                    navController.navigate(OpenDroidRoutes.PLAN)
                },
                onNavigateToMacros = {
                    navController.navigate(OpenDroidRoutes.MACROS)
                },
                onNavigateToLogs = {
                    navController.navigate(OpenDroidRoutes.LOGS)
                }
            )
        }

        composable(OpenDroidRoutes.PLAN) {
            val planViewModel: PlanViewModel = hiltViewModel()
            PlanScreen(
                viewModel = planViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(OpenDroidRoutes.MACROS) {
            val macroViewModel: MacroViewModel = hiltViewModel()
            MacrosScreen(
                viewModel = macroViewModel,
                onNavigateToRoutines = { navController.navigate(OpenDroidRoutes.ROUTINES) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(OpenDroidRoutes.LOGS) {
            val historyViewModel: HistoryViewModel = hiltViewModel()
            LogsScreen(
                viewModel = historyViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(OpenDroidRoutes.BENCHMARK) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            BenchmarkScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(OpenDroidRoutes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(OpenDroidRoutes.ABOUT) {
            AboutScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(OpenDroidRoutes.TERMS_OF_USE) {
            TermsOfUseScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(OpenDroidRoutes.HELP_CENTER) {
            HelpCenterScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(OpenDroidRoutes.LICENSE) {
            LicenseScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(OpenDroidRoutes.AUTO_REPLY_SETTINGS) {
            val settingsRepo = hiltViewModel<AutoReplyViewModel>().settingsRepository
            AutoReplySettingsScreen(
                settingsRepository = settingsRepo,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(OpenDroidRoutes.NOTIFICATION_HISTORY) {
            val notifDao = hiltViewModel<NotificationHistoryViewModel>().notificationDao
            NotificationHistoryScreen(
                notificationDao = notifDao,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(OpenDroidRoutes.PERMISSIONS) {
            PermissionsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(OpenDroidRoutes.CRASH_LOG) {
            CrashLogScreen(
                viewModel = hiltViewModel(),
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(OpenDroidRoutes.ROUTINES) {
            val routineViewModel: com.opendroid.ai.ui.viewmodel.RoutineViewModel = hiltViewModel()
            com.opendroid.ai.ui.screens.RoutinesScreen(
                viewModel = routineViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * The three places the app is used from.
 *
 * Six tabs made the bar a strip of equal buttons, and four of them were things
 * you look at rather than places you work: Plan, Macros and Logs are now reached
 * from Settings. What is left is the conversation, what the agent knows, and how
 * it is set up.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Chat : Screen("chat", "Chat", Icons.Default.Chat)

    // Psychology, not a star: the tab holds what the agent remembers and has
    // learned, and a star says "favourites" in every other app on the phone.
    object Memory : Screen("memory", "Memory", Icons.Default.Psychology)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainDashboard(
    onNavigateToBenchmark: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToTermsOfUse: () -> Unit,
    onNavigateToHelpCenter: () -> Unit,
    onNavigateToLicense: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAutoReply: () -> Unit = {},
    onNavigateToNotificationHistory: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToCrashLog: () -> Unit = {},
    onNavigateToRoutines: () -> Unit = {},
    onNavigateToPlan: () -> Unit = {},
    onNavigateToMacros: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {}
) {
    val context = LocalContext.current

    // Start the service as soon as RECORD_AUDIO is granted, and keep checking on every
    // resume - not just once on first composition - so a user who grants the microphone
    // from Settings > Permissions (rather than at onboarding) gets the service started
    // immediately on returning here, with no app restart required.
    var recordAudioServiceStarted by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted && !recordAudioServiceStarted) {
                    recordAudioServiceStarted = true
                    com.opendroid.ai.core.service.OpenDroidService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var currentTab by remember { mutableStateOf<Screen>(Screen.Chat) }

    val chatViewModel: ChatViewModel = hiltViewModel()
    val planViewModel: PlanViewModel = hiltViewModel()
    val memoryViewModel: MemoryViewModel = hiltViewModel()
    val macroViewModel: MacroViewModel = hiltViewModel()
    val historyViewModel: HistoryViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val tabs = listOf(
        Screen.Chat,
        Screen.Memory,
        Screen.Settings
    )

    val colors = LocalOpenDroidColors.current

    Scaffold(
        bottomBar = {
            OpenDroidNavBar(
                tabs = tabs,
                current = currentTab,
                onSelect = { currentTab = it },
            )
        },
        containerColor = colors.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {
            // The screen travels the same way the pocket does. Cutting between
            // tabs left the bar as the only thing that moved, so the bar looked
            // like an animation playing over a screen that had already changed;
            // a short slide in the direction of travel makes the two one gesture.
            // Small distance and heavy on the fade - a full-width slide on a
            // screen this dense reads as the page being dragged.
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val forward = tabs.indexOf(targetState) > tabs.indexOf(initialState)
                    val shift = { full: Int -> full / 12 }
                    val move = tween<IntOffset>(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)
                    (
                        slideInHorizontally(move) { if (forward) shift(it) else -shift(it) } +
                            fadeIn(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing))
                    ) togetherWith (
                        slideOutHorizontally(move) { if (forward) -shift(it) else shift(it) } +
                            // The outgoing screen leaves sooner than the incoming
                            // one arrives, so the two are never both at half
                            // opacity over the same pixels - which is what makes a
                            // cross-fade look like a smear.
                            fadeOut(tween(NAV_TRANSITION_MS / 2, easing = FastOutSlowInEasing))
                    ) using SizeTransform(clip = false)
                },
                label = "tabContent",
            ) { tab ->
            when (tab) {
                Screen.Chat -> ChatScreen(viewModel = chatViewModel)
                Screen.Memory -> MemoryScreen(viewModel = memoryViewModel)
                Screen.Settings -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToBenchmark = onNavigateToBenchmark,
                    onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy,
                    onNavigateToTermsOfUse = onNavigateToTermsOfUse,
                    onNavigateToHelpCenter = onNavigateToHelpCenter,
                    onNavigateToLicense = onNavigateToLicense,
                    onNavigateToAbout = onNavigateToAbout,
                    onNavigateToAutoReply = onNavigateToAutoReply,
                    onNavigateToNotificationHistory = onNavigateToNotificationHistory,
                    onNavigateToPermissions = onNavigateToPermissions,
                    onNavigateToCrashLog = onNavigateToCrashLog,
                    onNavigateToRoutines = onNavigateToRoutines,
                    onNavigateToPlan = onNavigateToPlan,
                    onNavigateToMacros = onNavigateToMacros,
                    onNavigateToLogs = onNavigateToLogs
                )
            }
            }
        }
    }
}

/**
 * How long a tab change takes, for the bar and the screen alike.
 *
 * One number, used by both, because two animations of nearly the same length are
 * exactly what a mismatch looks like.
 */
private const val NAV_TRANSITION_MS = 340

/**
 * How high the line stands where a tab is selected, as a fraction of the
 * distance between two tabs.
 *
 * A raised cosine, not a pair of cubics joined by a straight segment. The old
 * shape had four places where the curvature jumped, and a flat top the icon sat
 * awkwardly on; this is smooth everywhere, so the line has one continuous bend
 * and the crest is a crest rather than a plateau.
 *
 * @param slotsFromCentre distance from the selected tab, in tab widths
 */
private fun navCrest(slotsFromCentre: Float): Float {
    // A flat top with rounded shoulders, not a bell. A bell peaks at one point,
    // so the icon sits under a slope on both sides; a plateau wide enough for the
    // icon means the line runs level over it and turns down clear of it - which
    // is what makes it read as wrapping around the tab rather than passing above.
    val flat = 0.26f
    val shoulder = 0.44f
    val t = kotlin.math.abs(slotsFromCentre)
    if (t <= flat) return 1f
    if (t >= flat + shoulder) return 0f
    // Smootherstep, not smoothstep: its ends are flatter still and its middle
    // steeper, so the shoulder tucks in under the plateau instead of leaning away
    // from it. That tuck is what makes the pocket look like it curls around the
    // tab rather than ramping up to it.
    val x = (t - flat) / shoulder
    return 1f - x * x * x * (x * (x * 6f - 15f) + 10f)
}

/**
 * The bottom bar: a pill with a line that lifts around the selected tab.
 *
 * Three tabs is the count where a standard Material bar looks sparse - three
 * equal buttons in a full-width strip, most of it empty - so the bar is a pill
 * inset from the edges with a shape of its own.
 *
 * Everything here is driven by ONE animated number: where the crest is. The line
 * takes its height from it, the icons take their lift from it, and the colours
 * take their blend from it. Three separate animations - a spring for the line, a
 * tween for the tint, another spring for the lift - are what made switching tabs
 * feel heavy: they started together and finished apart, so the icon was still
 * arriving after the line had settled. Now they cannot disagree.
 */
@Composable
private fun OpenDroidNavBar(
    tabs: List<Screen>,
    current: Screen,
    onSelect: (Screen) -> Unit,
) {
    val colors = LocalOpenDroidColors.current
    val selectedIndex = tabs.indexOf(current).coerceAtLeast(0)
    // Soft and all but critically damped. Stiff enough to arrive without delay
    // reads as abrupt on a shape this large - the pocket crosses a third of the
    // screen - so it glides instead, with no bounce at the end to draw attention
    // to the arrival.
    // Deliberately NOT read through `by`. Every read of this value happens inside
    // a draw or layout lambda below, so a frame of the animation costs a redraw
    // rather than a recomposition. Read here in composition instead, the whole
    // row - three icons, three labels, their text layout - was rebuilt sixty
    // times a second, which is what stopped it feeling like sixty frames.
    // A tween, not a spring, and the same one the screen behind it uses. A spring
    // has an asymptotic tail - it is nearly there long before it is there - so
    // the pocket was still creeping into place after the screen had finished
    // changing. One duration and one easing shared with the content is what makes
    // the bar and the screen read as a single movement rather than two.
    val indicator = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing),
        label = "navIndicator",
    )

    // A slow breath on the red. Read inside the draw lambda like everything else,
    // so the pulse costs a redraw and nothing more. Long and shallow on purpose:
    // a bar that blinks at you is a bar you stop being able to ignore, and this
    // one sits on screen the whole time the app is open.
    val breath = rememberInfiniteTransition(label = "navGlow").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "navGlowValue",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                // Taller than a bar needs to be for its content, because the crest
                // has to fit above the icon rather than behind it.
                .height(100.dp)
                .clip(RoundedCornerShape(50.dp))
                // Barely above the page rather than a step above it. The bar wants
                // to be the darkest thing on the screen with only the line and the
                // pocket reading on it; the card colour was light enough to make
                // the bar itself the thing you noticed.
                .background(colors.surface)
        ) {
            val density = LocalDensity.current
            // The tabs are inset from the pill's ends, so that even the outer
            // crests have room for both flanks. Laid edge to edge, the first and
            // last crest ran off the side and the pill sliced the outer flank off
            // at full height - one tab domed, the other two half-domed.
            val edgeInset = 26.dp
            val slotWidth = (maxWidth - edgeInset * 2) / tabs.size
            val slotWidthPx = with(density) { slotWidth.toPx() }
            val edgeInsetPx = with(density) { edgeInset.toPx() }
            val heightPx = with(density) { 100.dp.toPx() }
            val accent = colors.accentRed
            val liftPx = with(density) { 4.dp.toPx() }

            // Held across frames and rewound rather than rebuilt. Two fresh Paths
            // per frame is two allocations sixty times a second, and the garbage
            // they leave is collected in pauses long enough to drop a frame - the
            // one thing a bar whose whole job is to move smoothly cannot afford.
            val crestPath = remember { Path() }
            val fillPath = remember { Path() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Well clear of the pill's rounded bottom. At 12dp the flat run
                // either side of the pocket sat inside the corner radius and was
                // clipped away at both ends, so the bar appeared to have lost its
                // baseline entirely.
                val baseY = heightPx - with(density) { 17.dp.toPx() }
                // The plateau runs about 20dp above the top of the icon and 10dp
                // below the top of the bar - as tall as the pill can carry it
                // without the stroke touching its own edge.
                val rise = with(density) { 73.dp.toPx() }
                val centreX = edgeInsetPx + slotWidthPx * (indicator.value + 0.5f)

                // Sampled rather than built from control points: the crest is a
                // function, so the curve is exactly that function and not an
                // approximation of it stitched together at the joins.
                // 72 samples across the bar: about one every five pixels, which is
                // finer than the 2.5dp stroke can show. 120 cost more to stroke
                // five times over and looked identical.
                val steps = 72
                val path = crestPath
                path.rewind()
                for (i in 0..steps) {
                    val x = size.width * i / steps
                    val y = baseY - rise * navCrest((x - centreX) / slotWidthPx)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                // Everything under the curve is dark, right down to the bottom of
                // the bar - the pocket and the strip either side of it are one
                // region, because they are one region. Cutting the fill back to
                // the dome to make the baseline visible traded the darkness away
                // for the line; the line is made to carry instead.
                fillPath.rewind()
                fillPath.addPath(path)
                fillPath.lineTo(size.width, heightPx)
                fillPath.lineTo(0f, heightPx)
                fillPath.close()
                drawPath(path = fillPath, color = colors.background)

                val pulse = breath.value

                // The outline around the bar itself. The crest line only ever ran
                // across the middle, so the pill had no edge of its own and its
                // bottom in particular disappeared into the page behind it.
                // Inset by half the stroke, or the clip takes the outer half.
                val borderWidth = with(density) { 1.5f.dp.toPx() }
                drawRoundRect(
                    color = accent.copy(alpha = 0.10f + 0.22f * pulse),
                    topLeft = Offset(borderWidth / 2f, borderWidth / 2f),
                    size = Size(size.width - borderWidth, size.height - borderWidth),
                    cornerRadius = CornerRadius(
                        with(density) { 50.dp.toPx() },
                        with(density) { 50.dp.toPx() },
                    ),
                    style = Stroke(width = borderWidth),
                )

                // The crest, drawn three times: two wide faint passes for the glow
                // and one solid pass on top. Compose cannot blur inside a draw
                // scope on every version this app runs on, so the falloff is built
                // out of overlapping strokes - few enough to stay cheap, enough
                // that the edge does not band.
                // All the way out to nothing at both ends. Holding them at a dim
                // red kept the line alive right up to the pill's corner, where it
                // has nothing left to do - the pill's own outline is what carries
                // the edge there now, so this one can leave.
                val strokeBrush = Brush.horizontalGradient(
                    0.00f to Color.Transparent,
                    0.06f to accent.copy(alpha = 0.15f),
                    0.20f to accent,
                    0.80f to accent,
                    0.94f to accent.copy(alpha = 0.15f),
                    1.00f to Color.Transparent,
                )
                // Four passes rather than two, and much wider. A thin halo reads as
                // light escaping inward from the line; the point of a glow is that
                // it falls off over a distance, and two narrow passes gave it
                // nowhere to fall off over.
                // Three passes, not four. Each one strokes a 72-segment path, and
                // the fourth was doing less for the falloff than it cost.
                listOf(
                    24.dp to 0.035f * pulse,
                    14.dp to 0.055f * pulse,
                    7.dp to 0.085f * pulse,
                ).forEach { (width, alpha) ->
                    drawPath(
                        path = path,
                        brush = strokeBrush,
                        alpha = alpha,
                        style = Stroke(width = with(density) { width.toPx() }, cap = StrokeCap.Round),
                    )
                }
                drawPath(
                    path = path,
                    brush = strokeBrush,
                    style = Stroke(width = with(density) { 2.5f.dp.toPx() }, cap = StrokeCap.Round),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = edgeInset)
            ) {
                tabs.forEachIndexed { index, tab ->
                    // Every use of the crest below is inside a graphicsLayer or an
                    // offset lambda, so none of it is a composition read. The
                    // selected and unselected states are both drawn, at every
                    // moment, and cross-faded by layer alpha - which is a property
                    // the render thread can animate without Compose rebuilding
                    // anything. Tinting one copy would have meant recomposing to
                    // change the tint.
                    val crestOf = { navCrest(index - indicator.value) }
                    val riseOffset: Density.() -> IntOffset =
                        { IntOffset(0, -(liftPx * crestOf()).roundToInt()) }

                    // Sat low in the bar rather than centred in it. The crest has
                    // to arc OVER the icon to read as wrapping around the tab, and
                    // centred content left it nowhere to go: the line came up
                    // through the middle of the icon instead of above it.
                    Column(
                        modifier = Modifier
                            .width(slotWidth)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(tab) }
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(modifier = Modifier.offset(riseOffset)) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = colors.textSecondary,
                                modifier = Modifier
                                    .size(23.dp)
                                    .graphicsLayer { alpha = 1f - crestOf() },
                            )
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier
                                    .size(23.dp)
                                    .graphicsLayer { alpha = crestOf() },
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(modifier = Modifier.offset(riseOffset)) {
                            Text(
                                text = tab.title,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.graphicsLayer { alpha = 1f - crestOf() },
                            )
                            Text(
                                text = tab.title,
                                color = colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.graphicsLayer { alpha = crestOf() },
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        // The dot under the selected label, fading in with the
                        // pocket rather than blinking on once it has settled.
                        Box(
                            modifier = Modifier
                                .offset(riseOffset)
                                .graphicsLayer { alpha = crestOf() }
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                    }
                }
            }
        }
    }
}
