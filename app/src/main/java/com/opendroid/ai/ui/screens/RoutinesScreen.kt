package com.opendroid.ai.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.opendroid.ai.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.data.models.HabitRoutine
import com.opendroid.ai.data.models.PlanStep
import com.opendroid.ai.data.models.RoutineStatus
import com.opendroid.ai.ui.theme.*
import com.opendroid.ai.ui.viewmodel.RoutineViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    viewModel: RoutineViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val suggestedRoutines by viewModel.suggestedRoutines.collectAsState()
    val activeRoutines by viewModel.activeRoutines.collectAsState()
    val recentEvents by viewModel.recentEvents.collectAsState()

    var isExecuting by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.routines_title),
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 19.sp,
                        letterSpacing = (-0.3).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = AppTheme.colors.textSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.triggerDetection()
                        Toast.makeText(context, "Scanning habit patterns...", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.routines_scan),
                            tint = AppTheme.colors.accentNeonGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.background)
            )
        },
        containerColor = AppTheme.colors.background,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── 1. SUGGESTED ROUTINES SECTION (AI DISCOVERED) ──────────
            if (suggestedRoutines.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.routines_discovered),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(suggestedRoutines, key = { it.id }) { routine ->
                    SuggestedRoutineCard(
                        routine = routine,
                        onApprove = {
                            viewModel.approveRoutine(routine.id)
                            Toast.makeText(context, "Routine '${routine.name}' automated!", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = {
                            viewModel.dismissRoutine(routine.id)
                            Toast.makeText(context, "Suggestion dismissed", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // ── 2. AUTOMATED ACTIVE ROUTINES ───────────────────────────
            item {
                Text(
                    text = "AUTOMATED ROUTINES (${activeRoutines.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (activeRoutines.isEmpty()) {
                item {
                    // The same empty state as Plan, Macros and Logs: no card, no
                    // outline, a quiet mark in a soft disc.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.cardBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = AppTheme.colors.textSecondary.copy(alpha = 0.75f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.routines_none),
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.routines_none_hint),
                            color = AppTheme.colors.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                items(activeRoutines, key = { it.id }) { routine ->
                    ActiveRoutineCard(
                        routine = routine,
                        isExecuting = isExecuting == routine.id,
                        onToggle = { isEnabled ->
                            viewModel.toggleRoutine(routine.id, isEnabled)
                        },
                        onExecute = {
                            isExecuting = routine.id
                            viewModel.executeRoutine(routine.id, context) { success, msg ->
                                isExecuting = null
                                Toast.makeText(
                                    context,
                                    if (success) "Routine completed: $msg" else "Execution failed: $msg",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onDelete = {
                            viewModel.deleteRoutine(routine.id)
                        }
                    )
                }
            }

            // ── 3. PRE-BUILT TEMPLATES ──────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.routines_templates),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                RoutineTemplateCard(
                    title = "Morning routine",
                    description = "Read calendar → Summarize today's meetings → Check notifications → Task list → Morning briefing",
                    trigger = "Every weekday at 9:00 AM",
                    onActivate = {
                        viewModel.triggerDetection()
                        Toast.makeText(context, "Morning Routine template activated!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                RoutineTemplateCard(
                    title = "Work focus routine",
                    description = "Open Slack → Check Calendar → Read important notifications",
                    trigger = "Every weekday at 9:30 AM",
                    onActivate = {
                        viewModel.triggerDetection()
                        Toast.makeText(context, "Work Focus template activated!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                RoutineTemplateCard(
                    title = "Evening wrap-up",
                    description = "Check tomorrow's calendar → Check unread notifications → Daily summary",
                    trigger = "Daily at 9:00 PM",
                    onActivate = {
                        viewModel.triggerDetection()
                        Toast.makeText(context, "Evening Wrap-up template activated!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // ── 4. HABIT LEARNING ANALYTICS ────────────────────────────
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppTheme.colors.borderColor, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                tint = AppTheme.colors.accentPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.routines_habit_learning),
                                style = MaterialTheme.typography.titleMedium,
                                color = AppTheme.colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tracked Events: ${recentEvents.size} recent activities logged.\nOpenDroid securely analyzes on-device app switches to learn your daily routines without cloud data transfer.",
                            color = AppTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestedRoutineCard(
    routine: HabitRoutine,
    onApprove: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.colors.accentNeonGreen, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = AppTheme.colors.accentNeonGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.routines_detected),
                        color = AppTheme.colors.accentNeonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = AppTheme.colors.surface,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${(routine.confidence * 100).toInt()}% match",
                        color = AppTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = routine.suggestionMessage.ifBlank { "I noticed you usually do these tasks. Would you like me to automate them?" },
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${routine.triggerLabel}",
                color = AppTheme.colors.accentCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )

            if (routine.detectedActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    routine.detectedActions.forEach { action ->
                        Surface(
                            color = AppTheme.colors.surface,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderColor)
                        ) {
                            Text(
                                text = action,
                                color = AppTheme.colors.textSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable suggested steps preview
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Proposed Automation (${routine.suggestedSteps.size} steps)",
                    color = AppTheme.colors.accentPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AppTheme.colors.accentPurple,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    routine.suggestedSteps.forEachIndexed { idx, step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${idx + 1}.",
                                color = AppTheme.colors.accentNeonGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(20.dp)
                            )
                            Text(
                                text = step.description,
                                color = AppTheme.colors.textPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accentGreenButton),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.routines_approve),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderColor)
                ) {
                    Text(
                        text = "Dismiss",
                        color = AppTheme.colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveRoutineCard(
    routine: HabitRoutine,
    isExecuting: Boolean,
    onToggle: (Boolean) -> Unit,
    onExecute: () -> Unit,
    onDelete: () -> Unit
) {
    val isEnabled = routine.status == RoutineStatus.ACTIVE || routine.status == RoutineStatus.APPROVED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isEnabled) AppTheme.colors.accentCyan else AppTheme.colors.borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routine.name,
                        color = AppTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${routine.triggerLabel}",
                        color = AppTheme.colors.accentCyan,
                        fontSize = 12.sp,
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppTheme.colors.accentGreenButton,
                        uncheckedThumbColor = AppTheme.colors.textSecondary,
                        uncheckedTrackColor = AppTheme.colors.surface
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = routine.description,
                color = AppTheme.colors.textSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onExecute,
                    enabled = !isExecuting,
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surface),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.accentNeonGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = AppTheme.colors.accentNeonGreen,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.routines_running), color = AppTheme.colors.accentNeonGreen, fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = AppTheme.colors.accentNeonGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.routines_run_now),
                            color = AppTheme.colors.accentNeonGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = AppTheme.colors.accentRed
                    )
                }
            }
        }
    }
}

@Composable
fun RoutineTemplateCard(
    title: String,
    description: String,
    trigger: String,
    onActivate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.colors.borderColor, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = AppTheme.colors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$trigger",
                    color = AppTheme.colors.accentCyan,
                    fontSize = 11.sp,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onActivate) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = stringResource(R.string.routines_activate),
                    tint = AppTheme.colors.accentNeonGreen
                )
            }
        }
    }
}


