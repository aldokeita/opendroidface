package com.opendroid.ai.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
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
import com.opendroid.ai.data.models.Macro
import com.opendroid.ai.data.models.PlanStep
import com.opendroid.ai.data.models.StepStatus
import com.opendroid.ai.ui.theme.*
import com.opendroid.ai.ui.viewmodel.MacroViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacrosScreen(
    viewModel: MacroViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToRoutines: () -> Unit = {},
) {
    val colors = LocalOpenDroidColors.current
    val macros by viewModel.macros.collectAsState()
    
    var isAddingMacro by remember { mutableStateOf(false) }
    var newMacroName by remember { mutableStateOf("") }
    var newMacroTrigger by remember { mutableStateOf("") }
    
    // Steps for the macro currently being built
    val macroSteps = remember { mutableStateListOf<PlanStep>() }
    
    // Step inputs
    var stepDesc by remember { mutableStateOf("") }
    var stepAction by remember { mutableStateOf("") }
    var stepParamKey by remember { mutableStateOf("") }
    var stepParamVal by remember { mutableStateOf("") }
    var stepFallback by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.macros_title),
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
                    IconButton(
                        onClick = { isAddingMacro = !isAddingMacro }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.macros_create),
                            tint = colors.accentNeonGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        containerColor = colors.background,
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
            // Habit & Routine Detection Entry Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToRoutines() },
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.routines_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.routines_subtitle),
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.macros_view_routines),
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            // Expandable Macro Creation Panel
            if (isAddingMacro) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.macros_new),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = newMacroName,
                                onValueChange = { newMacroName = it },
                                label = { Text(stringResource(R.string.macros_name), fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentNeonGreen,
                                    unfocusedBorderColor = colors.borderColor,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newMacroTrigger,
                                onValueChange = { newMacroTrigger = it },
                                label = { Text(stringResource(R.string.macros_trigger_phrase), fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentNeonGreen,
                                    unfocusedBorderColor = colors.borderColor,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = colors.borderColor)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Steps in custom macro
                            Text("Macro Steps Sequence (${macroSteps.size})", fontSize = 12.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            macroSteps.forEachIndexed { idx, st ->
                                Text(
                                    text = buildString {
                                        append("Step ${idx + 1}: ${st.description} [${st.action}]")
                                        if (st.fallback.isNotBlank()) append(" → fallback: ${st.fallback}")
                                    },
                                    fontSize = 11.sp,
                                    color = colors.accentNeonGreen,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.macros_add_step_details), fontSize = 11.sp, color = colors.textSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            OutlinedTextField(
                                value = stepDesc,
                                onValueChange = { stepDesc = it },
                                label = { Text(stringResource(R.string.macros_step_desc), fontSize = 11.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentNeonGreen,
                                    unfocusedBorderColor = colors.borderColor,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = stepAction,
                                onValueChange = { stepAction = it },
                                label = { Text(stringResource(R.string.macros_action_type), fontSize = 11.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentNeonGreen,
                                    unfocusedBorderColor = colors.borderColor,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = stepParamKey,
                                    onValueChange = { stepParamKey = it },
                                    label = { Text(stringResource(R.string.macros_param_key), fontSize = 11.sp) },
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
                                    value = stepParamVal,
                                    onValueChange = { stepParamVal = it },
                                    label = { Text(stringResource(R.string.macros_param_value), fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accentNeonGreen,
                                        unfocusedBorderColor = colors.borderColor,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = stepFallback,
                                onValueChange = { stepFallback = it },
                                label = { Text(stringResource(R.string.macros_fallback), fontSize = 11.sp) },
                                supportingText = {
                                    Text(stringResource(R.string.macros_fallback_hint), fontSize = 10.sp)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentNeonGreen,
                                    unfocusedBorderColor = colors.borderColor,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (stepDesc.isNotBlank() && stepAction.isNotBlank()) {
                                        val params = if (stepParamKey.isNotBlank()) mapOf(stepParamKey to stepParamVal) else emptyMap()
                                        macroSteps.add(
                                            PlanStep(
                                                stepId = UUID.randomUUID().toString(),
                                                order = macroSteps.size + 1,
                                                description = stepDesc,
                                                action = stepAction,
                                                params = params,
                                                fallback = stepFallback.trim(),
                                                status = StepStatus.PENDING
                                            )
                                        )
                                        stepDesc = ""
                                        stepAction = ""
                                        stepParamKey = ""
                                        stepParamVal = ""
                                        stepFallback = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentPurple, contentColor = colors.textPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(stringResource(R.string.macros_add_step), fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        isAddingMacro = false
                                        macroSteps.clear()
                                    }
                                ) {
                                    Text(stringResource(R.string.common_discard), color = colors.accentRed)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (newMacroName.isNotBlank() && newMacroTrigger.isNotBlank() && macroSteps.isNotEmpty()) {
                                            viewModel.saveMacro(
                                                Macro(
                                                    id = UUID.randomUUID().toString(),
                                                    name = newMacroName,
                                                    trigger = newMacroTrigger,
                                                    steps = macroSteps.toList()
                                                )
                                            )
                                            newMacroName = ""
                                            newMacroTrigger = ""
                                            macroSteps.clear()
                                            isAddingMacro = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentNeonGreen, contentColor = colors.background),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.macros_save), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Macros List Section
            if (macros.isNotEmpty()) {
                items(macros) { mc ->
                    MacroCard(
                        macro = mc,
                        onToggle = { isEnabled -> viewModel.toggleMacro(mc.id, isEnabled) },
                        onDelete = { viewModel.deleteMacro(mc.id) }
                    )
                }
            } else {
                item {
                    // The same empty state the other two screens use: a quiet mark
                    // in a soft disc, a title, and a line saying how something
                    // gets here. One grey sentence floating in a 160dp box said
                    // nothing about what to do next.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 56.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(colors.cardBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = colors.textSecondary.copy(alpha = 0.75f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.macros_none),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.macros_none_hint),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MacroCard(
    macro: Macro,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalOpenDroidColors.current
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = macro.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Trigger: \"${macro.trigger}\"",
                        fontSize = 12.sp,
                        color = colors.accentNeonGreen
                    )
                }
                Switch(
                    checked = macro.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.accentNeonGreen,
                        checkedTrackColor = colors.accentNeonGreen.copy(alpha = 0.5f)
                    )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = colors.borderColor)
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${macro.steps.size} scheduled steps in sequence",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.macros_expand),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    macro.steps.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(colors.accentCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${step.description} [${step.action}]",
                                fontSize = 11.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!macro.isSystem) {
                        Button(
                            onClick = onDelete,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentRed.copy(alpha = 0.1f), contentColor = colors.accentRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.macros_delete), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}


