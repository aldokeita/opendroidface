package com.opendroid.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
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
            when (currentTab) {
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
    val flat = 0.30f
    val shoulder = 0.42f
    val t = kotlin.math.abs(slotsFromCentre)
    if (t <= flat) return 1f
    if (t >= flat + shoulder) return 0f
    // Smoothstep down the shoulder: flat where it meets the plateau and flat
    // again where it meets the baseline, so there is no corner at either join.
    val x = (t - flat) / shoulder
    return 1f - x * x * (3f - 2f * x)
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
    // Stiff and barely under-damped: quick to arrive, with just enough overshoot
    // to read as travel rather than as a jump. The old 420 stiffness took long
    // enough to feel like the bar was thinking about it.
    val indicator by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 900f),
        label = "navIndicator",
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
                .height(88.dp)
                .clip(RoundedCornerShape(44.dp))
                // A step lighter than the page. The reference bar is black on a
                // pale screen; here the screen is already near-black, so the bar
                // has to come up rather than down to read as an object on it.
                .background(colors.cardBackground)
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
            val heightPx = with(density) { 88.dp.toPx() }
            val accent = colors.accentRed
            val liftPx = with(density) { 4.dp.toPx() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseY = heightPx - with(density) { 12.dp.toPx() }
                // The plateau runs about 17dp above the top of the icon, so the
                // icon sits inside the pocket with air around it rather than
                // touching its ceiling.
                val rise = with(density) { 58.dp.toPx() }
                val centreX = edgeInsetPx + slotWidthPx * (indicator + 0.5f)

                // Sampled rather than built from control points: the crest is a
                // function, so the curve is exactly that function and not an
                // approximation of it stitched together at the joins.
                val steps = 120
                val path = Path()
                for (i in 0..steps) {
                    val x = size.width * i / steps
                    val y = baseY - rise * navCrest((x - centreX) / slotWidthPx)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                // The dome the curve encloses, filled a shade apart from the rest
                // of the bar. Without it the line is a line drawn across a flat
                // surface; with it the selected tab sits in a pocket the line is
                // the edge of, which is what the reference does.
                val fill = Path().apply {
                    addPath(path)
                    lineTo(size.width, heightPx)
                    lineTo(0f, heightPx)
                    close()
                }
                // Both the fill and the stroke fade at the same two stops, so the
                // pocket and its edge stop existing together rather than one being
                // clipped by the pill while the other tapers.
                val fadeStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.13f to accent,
                    0.87f to accent,
                    1.00f to Color.Transparent,
                )
                drawPath(
                    path = fill,
                    brush = Brush.horizontalGradient(
                        *fadeStops.map { (at, c) -> at to c.copy(alpha = c.alpha * 0.10f) }.toTypedArray()
                    ),
                )
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(*fadeStops),
                    style = Stroke(width = with(density) { 2.dp.toPx() }, cap = StrokeCap.Round),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = edgeInset)
            ) {
                tabs.forEachIndexed { index, tab ->
                    // The same crest the line is drawn from, sampled at this tab's
                    // centre. At 1 the tab is fully selected; between stops it is
                    // partly both, which is what makes the handover continuous.
                    val crest = navCrest(index - indicator)
                    val tint = lerp(colors.textSecondary, accent, crest)
                    val labelColor = lerp(colors.textSecondary, colors.textPrimary, crest)
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
                            .padding(bottom = 13.dp),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = tint,
                            modifier = Modifier
                                .offset { IntOffset(0, -(liftPx * crest).roundToInt()) }
                                .size(23.dp),
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tab.title,
                            color = labelColor,
                            fontSize = 11.sp,
                            fontWeight = if (crest > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.offset { IntOffset(0, -(liftPx * crest).roundToInt()) },
                        )
                    }
                }
            }
        }
    }
}
