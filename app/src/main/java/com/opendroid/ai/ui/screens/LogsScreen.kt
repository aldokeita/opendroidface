package com.opendroid.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.opendroid.ai.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.data.db.entities.TaskHistoryEntity
import com.opendroid.ai.data.db.entities.UnknownActionEntity
import com.opendroid.ai.ui.theme.*
import com.opendroid.ai.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
) {
    val colors = LocalOpenDroidColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Executions", "Errors")

    val history by viewModel.taskHistory.collectAsState()
    val actionErrors by viewModel.unknownActions.collectAsState()
    var selectedPlanForMacro by remember { mutableStateOf<String?>(null) }
    var macroName by remember { mutableStateOf("") }
    var macroSaveError by remember { mutableStateOf<String?>(null) }

    val saveableLogIds = remember(history) {
        history.groupBy { it.planId }
            .filter { (planId, entries) ->
                planId.isNotBlank() && planId != "n/a" && entries.isNotEmpty() && entries.all { it.success }
            }
            .mapNotNull { (_, entries) ->
                entries.minWithOrNull(compareBy<TaskHistoryEntity> { it.timestamp }.thenBy { it.id })?.id
            }
            .toSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.logs_title),
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 19.sp,
                        letterSpacing = (-0.3).sp
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = colors.textSecondary,
                            )
                        }
                    }
                },
                actions = {
                    val hasLogs = if (selectedTab == 0) history.isNotEmpty() else actionErrors.isNotEmpty()
                    if (hasLogs) {
                        IconButton(
                            onClick = {
                                if (selectedTab == 0) {
                                    viewModel.clearTaskHistory()
                                } else {
                                    viewModel.clearUnknownActions()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.logs_clear),
                                tint = colors.accentRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        containerColor = colors.background,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // A segmented control, not a tab row. Two underlined captions in neon
            // caps read as a page of a document; two segments in a track read as
            // a switch between two lists, which is what this is.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.cardBackground)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    val container by animateColorAsState(
                        if (isSelected) colors.accentNeonGreen.copy(alpha = 0.16f) else Color.Transparent,
                        tween(200),
                        label = "segContainer",
                    )
                    val content by animateColorAsState(
                        if (isSelected) colors.accentNeonGreen else colors.textSecondary,
                        tween(200),
                        label = "segContent",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(container)
                            .clickable { selectedTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = content,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (selectedTab == 0) {
                    if (history.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(history) { log ->
                                HistoryLogCard(
                                    log = log,
                                    onSaveAsMacro = if (log.id in saveableLogIds) {
                                        {
                                            selectedPlanForMacro = log.planId
                                            macroName = "${log.description.take(40).ifBlank { "Completed task" }} macro"
                                            macroSaveError = null
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    } else {
                        EmptyStateView(
                            title = "No executions recorded yet",
                            subtitle = "Every step OpenDroid executes is archived here.",
                            icon = Icons.Default.History
                        )
                    }
                } else {
                    if (actionErrors.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(actionErrors) { error ->
                                UnknownActionCard(error = error)
                            }
                        }
                    } else {
                        EmptyStateView(
                            title = "Nothing has gone wrong",
                            subtitle = "Commands the agent could not recognise would be listed here.",
                            icon = Icons.Default.CheckCircle,
                            iconColor = colors.accentNeonGreen
                        )
                    }
                }
            }
        }
    }

    if (selectedPlanForMacro != null) {
        AlertDialog(
            onDismissRequest = {
                selectedPlanForMacro = null
                macroSaveError = null
            },
            title = { Text(stringResource(R.string.logs_save_macro)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.logs_macro_hint),
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = macroName,
                        onValueChange = { macroName = it },
                        label = { Text(stringResource(R.string.logs_macro_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    macroSaveError?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error, color = colors.accentRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCompletedTaskAsMacro(
                            planId = selectedPlanForMacro!!,
                            name = macroName
                        ) { error ->
                            if (error == null) {
                                selectedPlanForMacro = null
                                macroSaveError = null
                            } else {
                                macroSaveError = error
                            }
                        }
                    },
                    enabled = macroName.isNotBlank()
                ) {
                    Text(stringResource(R.string.macros_save_short))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPlanForMacro = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color = LocalOpenDroidColors.current.textSecondary,
) {
    val colors = LocalOpenDroidColors.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // A quiet mark inside a soft disc, not a 48dp filled circle. The old
            // one was the heaviest thing on a screen whose entire message is that
            // there is nothing here yet.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.cardBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor.copy(alpha = 0.75f),
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun UnknownActionCard(error: UnknownActionEntity) {
    val colors = LocalOpenDroidColors.current
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    val statusColor = when (error.fixStatus) {
        "AUTO_FIXED" -> colors.accentNeonGreen
        "REPLANNED" -> colors.accentCyan
        "FAILED" -> colors.accentRed
        else -> colors.accentNeonGreen
    }

    val statusText = when (error.fixStatus) {
        "AUTO_FIXED" -> "AUTO-FIXED"
        "REPLANNED" -> "REPLANNED"
        "FAILED" -> "FAILED"
        else -> error.fixStatus
    }

    val explanation = when (error.fixStatus) {
        "AUTO_FIXED" -> "Successfully auto-corrected by OpenDroid's Repair Engine."
        "REPLANNED" -> "Dynamically replanned and bypassed the unrecognized command."
        "FAILED" -> "Unrecognized system command failed execution."
        else -> "System anomaly tracked."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                Text(
                    text = dateFormat.format(Date(error.timestamp)),
                    fontSize = 10.sp,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Unrecognized: ${error.attemptedAction}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentPurple
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = "Goal: ${error.goal}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = colors.borderColor, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Text(
                        text = stringResource(R.string.logs_status),
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = explanation,
                        fontSize = 12.sp,
                        color = statusColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.background)
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.logs_expand),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun HistoryLogCard(
    log: TaskHistoryEntity,
    onSaveAsMacro: (() -> Unit)? = null
) {
    val colors = LocalOpenDroidColors.current
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Success Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (log.success) colors.accentNeonGreen.copy(alpha = 0.15f) else colors.accentRed.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (log.success) "SUCCESS" else "FAILED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (log.success) colors.accentNeonGreen else colors.accentRed
                    )
                }

                Text(
                    text = dateFormat.format(Date(log.timestamp)),
                    fontSize = 10.sp,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = log.description,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Module: ${log.actionType}",
                fontSize = 11.sp,
                color = colors.accentPurple
            )

            onSaveAsMacro?.let { save ->
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = save,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPurple),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.logs_save_macro), fontSize = 11.sp)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = colors.borderColor, modifier = Modifier.padding(vertical = 4.dp))
                    
                    if (log.paramsJson.isNotBlank() && log.paramsJson != "{}") {
                        Text(stringResource(R.string.logs_params), fontSize = 11.sp, color = colors.textSecondary)
                        Text(
                            text = log.paramsJson,
                            fontSize = 11.sp,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.background)
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (log.resultData != null) {
                        Text(stringResource(R.string.logs_result), fontSize = 11.sp, color = colors.textSecondary)
                        Text(
                            text = log.resultData,
                            fontSize = 11.sp,
                            color = colors.accentNeonGreen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.background)
                                .padding(8.dp)
                        )
                    }

                    if (log.errorMessage != null) {
                        Text(stringResource(R.string.logs_error), fontSize = 11.sp, color = colors.textSecondary)
                        Text(
                            text = log.errorMessage,
                            fontSize = 11.sp,
                            color = colors.accentRed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accentRed.copy(alpha = 0.05f))
                                .padding(8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.logs_expand),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}



