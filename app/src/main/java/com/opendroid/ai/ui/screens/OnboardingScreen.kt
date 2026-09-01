package com.opendroid.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import com.opendroid.ai.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.ui.face.RobotFace
import com.opendroid.ai.ui.theme.*
import com.opendroid.ai.ui.viewmodel.OnboardingViewModel

enum class OnboardingStage {
    INTRODUCTION,
    PERMISSION_PROMPT,
    PERMISSIONS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val colors = LocalOpenDroidColors.current
    val uiState by viewModel.uiState.collectAsState()

    var stage by remember { mutableStateOf(OnboardingStage.INTRODUCTION) }
    var showError by remember { mutableStateOf(false) }

    // A user returning to a stored profile skips straight past the introduction, as before.
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && uiState.name.isNotBlank() && uiState.dateOfBirth.isNotBlank()) {
            stage = OnboardingStage.PERMISSION_PROMPT
        }
    }

    // No top bar. Each stage opens with its own heading - "Hello! I am OpenDroid",
    // "Permissions Setup", "Required Permissions" - so a bar repeating a shorter
    // version of that in accent green added a second title, pinned the loudest
    // colour on the screen to a corner with nothing in it, and pushed the content
    // it labelled into the bottom two thirds.
    Scaffold(
        containerColor = colors.background
    ) { padding ->
        when (stage) {
            OnboardingStage.INTRODUCTION -> {
                IntroductionPanel(
                    name = uiState.name,
                    onNameChange = { viewModel.onNameChange(it); showError = false },
                    dob = uiState.dateOfBirth,
                    onDobChange = { viewModel.onDateOfBirthChange(it); showError = false },
                    showError = showError,
                    profileMustBeReentered = uiState.profileMustBeReentered,
                    storageError = uiState.storageError,
                    onContinue = {
                        if (uiState.name.isBlank() || uiState.dateOfBirth.isBlank()) {
                            showError = true
                        } else {
                            // The stage only advances once the profile is encrypted at rest.
                            viewModel.saveProfile { stage = OnboardingStage.PERMISSION_PROMPT }
                        }
                    },
                    modifier = Modifier.padding(padding)
                )
            }
            OnboardingStage.PERMISSION_PROMPT -> {
                PermissionPromptPanel(
                    onContinue = {
                        stage = OnboardingStage.PERMISSIONS
                    },
                    modifier = Modifier.padding(padding)
                )
            }
            OnboardingStage.PERMISSIONS -> {
                PermissionsPanel(
                    padding = padding,
                    onFinished = { viewModel.completeOnboarding(onFinished) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroductionPanel(
    name: String,
    onNameChange: (String) -> Unit,
    dob: String,
    onDobChange: (String) -> Unit,
    showError: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    profileMustBeReentered: Boolean = false,
    storageError: Boolean = false
) {
    val colors = LocalOpenDroidColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RobotFace(
            state = AgentState.Idle,
            modifier = Modifier.size(140.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onb_hello),
            fontFamily = Montserrat,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            letterSpacing = (-0.3).sp,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onb_intro),
            fontSize = 14.sp,
            color = colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (profileMustBeReentered) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your saved details could not be unlocked on this device, so they were " +
                        "not kept. Nothing was stored unencrypted - please enter them again.",
                color = colors.accentRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.onb_name_label), color = colors.textSecondary) },
            placeholder = { Text(stringResource(R.string.onb_name_hint), color = colors.textSecondary.copy(alpha = 0.6f)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accentNeonGreen,
                unfocusedBorderColor = colors.borderColor,
                focusedLabelColor = colors.accentNeonGreen,
                unfocusedLabelColor = colors.textSecondary,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.accentNeonGreen
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        var showDatePicker by remember { mutableStateOf(false) }

        OutlinedTextField(
            value = dob,
            onValueChange = onDobChange,
            label = { Text(stringResource(R.string.onb_birthday_label), color = colors.textSecondary) },
            placeholder = { Text(stringResource(R.string.onb_birthday_hint), color = colors.textSecondary.copy(alpha = 0.6f)) },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = stringResource(R.string.onb_birthday_pick),
                        tint = colors.accentNeonGreen
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accentNeonGreen,
                unfocusedBorderColor = colors.borderColor,
                focusedLabelColor = colors.accentNeonGreen,
                unfocusedLabelColor = colors.textSecondary,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.accentNeonGreen
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onContinue() }),
            modifier = Modifier.fillMaxWidth()
        )

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = parseDobToUtcMillis(dob),
                yearRange = 1900..java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                onDobChange(formatUtcMillisAsDob(millis))
                            }
                            showDatePicker = false
                        },
                        enabled = datePickerState.selectedDateMillis != null
                    ) { Text(stringResource(R.string.common_ok), color = colors.accentNeonGreen, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (showError) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onb_need_both),
                color = colors.accentRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (storageError) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onb_save_failed),
                color = colors.accentRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accentNeonGreen, contentColor = colors.background),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(stringResource(R.string.onb_start), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** Parses a typed MM/DD/YYYY value into UTC millis for the picker, or null if not parseable. */
private fun parseDobToUtcMillis(dob: String): Long? = runCatching {
    val format = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
        isLenient = false
    }
    format.parse(dob.trim())?.time
}.getOrNull()

/** Formats picker UTC millis as the MM/DD/YYYY string the rest of onboarding expects. */
private fun formatUtcMillisAsDob(millis: Long): String {
    val format = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    return format.format(java.util.Date(millis))
}

@Composable
fun PermissionPromptPanel(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalOpenDroidColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        RobotFace(
            state = AgentState.Idle,
            modifier = Modifier.size(140.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onb_perm_title),
            fontFamily = Montserrat,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            letterSpacing = (-0.3).sp,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onb_perm_subtitle),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onb_perm_body),
            fontSize = 14.sp,
            color = colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accentNeonGreen, contentColor = colors.background),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(stringResource(R.string.onb_perm_grant), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

