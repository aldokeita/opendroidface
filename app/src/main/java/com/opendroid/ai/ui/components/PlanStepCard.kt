package com.opendroid.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.data.models.PlanStep
import com.opendroid.ai.data.models.StepStatus
import com.opendroid.ai.ui.theme.*

enum class StepDisplayState {
    PENDING, RUNNING, COMPLETED, FAILED, AUTO_FIXING, REPAIRED, SKIPPED, BLOCKED
}

fun getDisplayState(step: PlanStep): StepDisplayState {
    val errorText = step.error?.lowercase() ?: ""
    val resultText = step.result?.lowercase() ?: ""
    
    return when {
        step.status == StepStatus.COMPLETED && (resultText.contains("auto-fixed") || resultText.contains("primary failed") || resultText.contains("repaired")) -> StepDisplayState.REPAIRED
        step.status == StepStatus.COMPLETED && resultText.contains("skipped") -> StepDisplayState.SKIPPED
        step.status == StepStatus.FAILED && errorText.contains("is not registered in ActionDispatcher") -> StepDisplayState.AUTO_FIXING
        step.status == StepStatus.FAILED && errorText.contains("blocked") -> StepDisplayState.BLOCKED
        step.status == StepStatus.COMPLETED -> StepDisplayState.COMPLETED
        step.status == StepStatus.RUNNING -> StepDisplayState.RUNNING
        step.status == StepStatus.FAILED -> StepDisplayState.FAILED
        else -> StepDisplayState.PENDING
    }
}

@Composable
fun PlanStepCard(
    step: PlanStep,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    onSaveEdit: (description: String, params: Map<String, String>) -> Unit = { _, _ -> },
    onDeleteStep: () -> Unit = {}
) {
    val colors = LocalOpenDroidColors.current
    var expanded by remember { mutableStateOf(false) }
    var isEditing by remember(step.stepId) { mutableStateOf(false) }
    var editDescription by remember(step.stepId, isEditing) { mutableStateOf(step.description) }
    var editParams by remember(step.stepId, isEditing) {
        mutableStateOf(step.params.map { (key, value) -> key to value })
    }
    val displayState = getDisplayState(step)

    val statusColor = when (displayState) {
        StepDisplayState.COMPLETED -> colors.accentNeonGreen
        StepDisplayState.RUNNING -> colors.accentCyan
        StepDisplayState.FAILED -> colors.accentRed
        StepDisplayState.AUTO_FIXING -> colors.accentPurple
        StepDisplayState.REPAIRED -> colors.accentPurple
        StepDisplayState.SKIPPED -> colors.textSecondary
        StepDisplayState.BLOCKED -> colors.accentOrange
        StepDisplayState.PENDING -> colors.textSecondary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isEditing) { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Circle badge for step order
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${step.order}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = step.description,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = if (expanded) Int.MAX_VALUE else 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editable && !isEditing) {
                        IconButton(
                            onClick = { isEditing = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit step",
                                tint = colors.accentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onDeleteStep,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete step",
                                tint = colors.accentRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Step status icon
                    Icon(
                        imageVector = when (displayState) {
                            StepDisplayState.COMPLETED -> Icons.Default.CheckCircle
                            StepDisplayState.RUNNING -> Icons.Default.Refresh
                            StepDisplayState.FAILED -> Icons.Default.Close
                            StepDisplayState.AUTO_FIXING -> Icons.Default.Build
                            StepDisplayState.REPAIRED -> Icons.Default.CheckCircle
                            StepDisplayState.SKIPPED -> Icons.Default.ArrowForward
                            StepDisplayState.BLOCKED -> Icons.Default.Warning
                            StepDisplayState.PENDING -> Icons.Default.PlayArrow
                        },
                        contentDescription = displayState.name,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isEditing) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = colors.borderColor, modifier = Modifier.padding(vertical = 4.dp))

                    Text("Action Module: ${step.action}", fontSize = 11.sp, color = colors.accentPurple, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Step Description", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentNeonGreen,
                            unfocusedBorderColor = colors.borderColor,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Parameters", fontSize = 11.sp, color = colors.textSecondary)

                    editParams.forEachIndexed { index, (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = key,
                                onValueChange = { newKey ->
                                    editParams = editParams.toMutableList().also { it[index] = newKey to value }
                                },
                                label = { Text("Key", fontSize = 10.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentNeonGreen,
                                    unfocusedBorderColor = colors.borderColor,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedTextField(
                                value = value,
                                onValueChange = { newValue ->
                                    editParams = editParams.toMutableList().also { it[index] = key to newValue }
                                },
                                label = { Text("Value", fontSize = 10.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentNeonGreen,
                                    unfocusedBorderColor = colors.borderColor,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { editParams = editParams.toMutableList().also { it.removeAt(index) } },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove parameter",
                                    tint = colors.accentRed,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = { editParams = editParams + ("" to "") },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = colors.accentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Parameter", fontSize = 11.sp, color = colors.accentCyan)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isEditing = false }) {
                            Text("Cancel", color = colors.textSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val cleanedParams = editParams
                                    .map { (k, v) -> k.trim() to v }
                                    .filter { it.first.isNotEmpty() }
                                    .toMap()
                                val cleanedDescription = editDescription.trim().ifEmpty { step.description }
                                onSaveEdit(cleanedDescription, cleanedParams)
                                isEditing = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentNeonGreen, contentColor = colors.background),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded && !isEditing) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = colors.borderColor, modifier = Modifier.padding(vertical = 4.dp))
                    
                    Text("Action Module: ${step.action}", fontSize = 11.sp, color = colors.accentPurple, fontFamily = FontFamily.Monospace)
                    
                    if (step.params.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Parameters:", fontSize = 11.sp, color = colors.textSecondary)
                        step.params.forEach { (key, valStr) ->
                            Text("- $key: $valStr", fontSize = 11.sp, color = colors.textPrimary, fontFamily = FontFamily.Monospace)
                        }
                    }

                    if (step.dependsOn.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Depends On Steps: ${step.dependsOn.joinToString()}", fontSize = 11.sp, color = colors.textSecondary, fontFamily = FontFamily.Monospace)
                    }

                    if (step.canParallelize) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Parallel execution supported", fontSize = 11.sp, color = colors.accentCyan)
                    }

                    if (step.fallback.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Fallback Routine:", fontSize = 11.sp, color = colors.textSecondary)
                        Text(step.fallback, fontSize = 11.sp, color = colors.textPrimary, fontFamily = FontFamily.Monospace)
                    }

                    if (step.result != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.background)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("Execution Result:", fontSize = 10.sp, color = colors.accentNeonGreen, fontWeight = FontWeight.Bold)
                                Text(step.result!!, fontSize = 11.sp, color = colors.textPrimary)
                            }
                        }
                    }

                    if (step.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val isHallucinationError = displayState == StepDisplayState.AUTO_FIXING
                        val errorAccent = if (isHallucinationError) colors.accentOrange else colors.accentRed
                        val errorBgColor = errorAccent.copy(alpha = 0.1f)
                        val errorTextDisplay = if (isHallucinationError) "Auto-fixing: The requested system action is currently being recovered and updated by the OpenDroid Repair Engine." else step.error!!

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(errorBgColor)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (isHallucinationError) "Repair Phase Active" else "Execution Error:",
                                    fontSize = 10.sp,
                                    color = errorAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(errorTextDisplay, fontSize = 11.sp, color = colors.textPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
