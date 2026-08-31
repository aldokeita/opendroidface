package com.opendroid.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
) {
    val colors = LocalOpenDroidColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Execution Logs", "Action Errors")

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
                        text = "Logs",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 19.sp,
                        letterSpacing = (-0.3).sp
                    )
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
                                contentDescription = "Clear logs",
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
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = colors.background,
                contentColor = colors.accentNeonGreen,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = colors.accentNeonGreen
                    )
                },
                divider = {
                    Divider(color = colors.borderColor)
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) colors.accentNeonGreen else colors.textSecondary
                            )
                        }
                    )
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
                            icon = Icons.Default.Info
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
                            title = "All systems fully aligned",
                            subtitle = "OpenDroid's Repair Engine has not encountered any unrecognized commands.",
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
            title = { Text("Save completed task as macro") },
            text = {
                Column {
                    Text(
                        "Only successful steps will be recorded. Credential, API-key, and token values are removed.",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = macroName,
                        onValueChange = { macroName = it },
                        label = { Text("Macro name") },
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
                    Text("Save macro")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPlanForMacro = null }) {
                    Text("Cancel")
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
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
                        text = "System Status Details:",
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
                    contentDescription = "Expand info",
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
                    Text("Save completed task as macro", fontSize = 11.sp)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = colors.borderColor, modifier = Modifier.padding(vertical = 4.dp))
                    
                    if (log.paramsJson.isNotBlank() && log.paramsJson != "{}") {
                        Text("Parameters:", fontSize = 11.sp, color = colors.textSecondary)
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
                        Text("Execution Result Data:", fontSize = 11.sp, color = colors.textSecondary)
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
                        Text("Diagnostic Error Log:", fontSize = 11.sp, color = colors.textSecondary)
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
                    contentDescription = "Expand info",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
