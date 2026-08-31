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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
 * The bottom bar: a floating pill with an indicator that actually travels.
 *
 * Three tabs is the count where a standard Material bar starts to look sparse -
 * three equal buttons in a full-width strip, most of it empty. A pill inset from
 * the edges gives the bar a shape of its own, and because there are only three
 * stops, the selected one can afford to carry its label while the other two are
 * icons alone. That is what makes the selection obvious without colour doing all
 * the work.
 *
 * The indicator is one moving thing rather than three that light up in turn: it
 * is measured against the bar's own width and slides, so switching tabs reads as
 * travel between two places rather than as two separate states blinking.
 */
@Composable
private fun OpenDroidNavBar(
    tabs: List<Screen>,
    current: Screen,
    onSelect: (Screen) -> Unit,
) {
    val colors = LocalOpenDroidColors.current
    val selectedIndex = tabs.indexOf(current).coerceAtLeast(0)
    // Spring, not tween: the slide has weight this way, and a bar this small
    // needs the overshoot to register as movement at all.
    val indicator by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
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
                .height(74.dp)
                .clip(RoundedCornerShape(37.dp))
                // Near-black rather than the card colour: the bar is meant to read
                // as a solid object laid on the screen, and the red only carries
                // if there is nothing else competing with it.
                .background(colors.surface)
        ) {
            val density = LocalDensity.current
            val slotWidth = maxWidth / tabs.size
            val slotWidthPx = with(density) { slotWidth.toPx() }
            val heightPx = with(density) { 74.dp.toPx() }
            val accent = colors.accentRed

            // The line that runs the width of the bar and swoops up around
            // whichever tab is selected. It is the whole animation: one continuous
            // stroke whose bend travels, rather than three states lighting up in
            // turn. Drawn on a Canvas because a path that has to be re-shaped every
            // frame cannot be built out of layout.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseY = heightPx - with(density) { 12.dp.toPx() }
                val rise = with(density) { 24.dp.toPx() }
                val centreX = slotWidthPx * (indicator + 0.5f)
                val half = slotWidthPx * 0.30f
                val ease = slotWidthPx * 0.22f

                val path = Path().apply {
                    moveTo(0f, baseY)
                    lineTo(centreX - half - ease, baseY)
                    // Two mirrored curves into and out of the notch, so the line
                    // lifts rather than steps.
                    cubicTo(
                        centreX - half + ease * 0.3f, baseY,
                        centreX - half - ease * 0.2f, baseY - rise,
                        centreX - half + ease * 0.5f, baseY - rise,
                    )
                    lineTo(centreX + half - ease * 0.5f, baseY - rise)
                    cubicTo(
                        centreX + half + ease * 0.2f, baseY - rise,
                        centreX + half - ease * 0.3f, baseY,
                        centreX + half + ease, baseY,
                    )
                    lineTo(size.width, baseY)
                }
                drawPath(
                    path = path,
                    color = accent,
                    style = Stroke(width = with(density) { 2.dp.toPx() }, cap = StrokeCap.Round),
                )
            }

            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    val isSelected = tab == current
                    val tint by animateColorAsState(
                        if (isSelected) accent else colors.textSecondary,
                        tween(220),
                        label = "navTint",
                    )
                    val labelColor by animateColorAsState(
                        if (isSelected) colors.textPrimary else colors.textSecondary,
                        tween(220),
                        label = "navLabel",
                    )
                    // The selected icon sits inside the notch, so it lifts with it.
                    // In pixels, and read inside the offset lambda: a value that
                    // changes every frame belongs in the layout pass, not in a
                    // recomposition.
                    val liftPx by animateFloatAsState(
                        if (isSelected) with(density) { (-6).dp.toPx() } else 0f,
                        spring(dampingRatio = 0.7f, stiffness = 400f),
                        label = "navLift",
                    )
                    Column(
                        modifier = Modifier
                            .width(slotWidth)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(tab) },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = tint,
                            modifier = Modifier
                                .offset { IntOffset(0, liftPx.roundToInt()) }
                                .size(23.dp),
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tab.title,
                            color = labelColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.offset { IntOffset(0, liftPx.roundToInt()) },
                        )
                    }
                }
            }
        }
    }
}
