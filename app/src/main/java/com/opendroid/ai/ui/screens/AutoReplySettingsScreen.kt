package com.opendroid.ai.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.opendroid.ai.R
import com.opendroid.ai.data.models.AutoReplyConfig
import com.opendroid.ai.data.repository.SettingsRepository
import com.opendroid.ai.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoReplySettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(AutoReplyConfig()) }
    var isLoading by remember { mutableStateOf(true) }

    var isNotificationPermissionGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isAccessibilityPermissionGranted by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationPermissionGranted = isNotificationServiceEnabled(context)
                isAccessibilityPermissionGranted = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        config = settingsRepository.autoReplyConfig.first()
        isLoading = false
    }

    var dbWriteJob by remember { mutableStateOf<Job?>(null) }

    // The debounce used to run on this composable's own scope, so leaving the
    // screen cancelled the write that had not fired yet - a long note typed
    // carefully and then backed out of was simply gone. The repository owns the
    // write now, and leaving flushes whatever is still pending.
    fun saveConfig(newConfig: AutoReplyConfig, debounce: Boolean = false) {
        config = newConfig
        dbWriteJob?.cancel()
        if (!debounce) {
            settingsRepository.saveAutoReplyConfigAsync(newConfig)
            return
        }
        dbWriteJob = scope.launch {
            delay(600)
            settingsRepository.saveAutoReplyConfigAsync(newConfig)
        }
    }

    // Whatever the last keystroke left behind goes to disk on the way out,
    // whether that is the back button, a task switch, or the screen going off.
    DisposableEffect(Unit) {
        onDispose {
            dbWriteJob?.cancel()
            settingsRepository.saveAutoReplyConfigAsync(config)
        }
    }

    val themeColors = AppTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auto_reply_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColors.surface,
                    titleContentColor = themeColors.textPrimary,
                    navigationIconContentColor = themeColors.textPrimary
                ),
                modifier = Modifier.border(0.5.dp, themeColors.borderColor.copy(alpha = 0.5f))
            )
        },
        containerColor = themeColors.background
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = themeColors.accentPurple)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    // Without this the keyboard covered whatever was being
                    // typed into: the long fields sit near the bottom, and a
                    // field you cannot see while writing into it is unusable.
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Permission Warning Card
                if (!isNotificationPermissionGranted || !isAccessibilityPermissionGranted) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, themeColors.accentRed, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = themeColors.accentRed.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.auto_reply_perms_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = themeColors.accentRed
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.auto_reply_perms_body),
                                fontSize = 13.sp,
                                color = themeColors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isNotificationPermissionGranted) {
                                    Button(
                                        onClick = {
                                            try {
                                                context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                                            } catch (e: Exception) {
                                                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.accentRed),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.auto_reply_grant_notif), fontSize = 10.sp, color = Color.White)
                                    }
                                }
                                if (!isAccessibilityPermissionGranted) {
                                    Button(
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                            } catch (e: Exception) {
                                                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.accentPurple),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(stringResource(R.string.auto_reply_grant_acc), fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // Global Toggle Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, themeColors.borderColor, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (config.globalEnabled) {
                            themeColors.accentPurple.copy(alpha = 0.08f)
                        } else {
                            themeColors.cardBackground
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.auto_reply_short),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeColors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (config.globalEnabled) "AI will auto-reply to messages after ${config.replyDelayMinutes} minutes"
                                else "Auto-reply is disabled",
                                fontSize = 13.sp,
                                color = themeColors.textSecondary
                            )
                        }
                        Switch(
                            checked = config.globalEnabled,
                            onCheckedChange = { saveConfig(config.copy(globalEnabled = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = themeColors.accentNeonGreen,
                                checkedTrackColor = themeColors.accentNeonGreen.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                if (config.globalEnabled) {
                    // Per-App Toggles
                    Text(
                        stringResource(R.string.auto_reply_apps),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeColors.textPrimary
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, themeColors.borderColor, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            AppToggleRow("WhatsApp", "💬", config.whatsappEnabled, themeColors) {
                                saveConfig(config.copy(whatsappEnabled = it))
                            }
                            Divider(color = themeColors.borderColor.copy(alpha = 0.5f))
                            AppToggleRow("SMS", "📱", config.smsEnabled, themeColors) {
                                saveConfig(config.copy(smsEnabled = it))
                            }
                            Divider(color = themeColors.borderColor.copy(alpha = 0.5f))
                            AppToggleRow("Email", "📧", config.emailEnabled, themeColors) {
                                saveConfig(config.copy(emailEnabled = it))
                            }
                        }
                    }

                    // Reply Delay Slider
                    Text(
                        stringResource(R.string.auto_reply_delay),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeColors.textPrimary
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, themeColors.borderColor, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    stringResource(R.string.auto_reply_wait),
                                    fontSize = 14.sp,
                                    color = themeColors.textSecondary
                                )
                                Text(
                                    "${config.replyDelayMinutes} minutes",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = themeColors.accentPurple
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = config.replyDelayMinutes.toFloat(),
                                onValueChange = {
                                    saveConfig(config.copy(replyDelayMinutes = it.toInt()))
                                },
                                valueRange = 1f..60f,
                                steps = 58,
                                colors = SliderDefaults.colors(
                                    thumbColor = themeColors.accentPurple,
                                    activeTrackColor = themeColors.accentPurple
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.auto_reply_min_short), fontSize = 12.sp, color = themeColors.textSecondary.copy(alpha = 0.6f))
                                Text(stringResource(R.string.auto_reply_max_short), fontSize = 12.sp, color = themeColors.textSecondary.copy(alpha = 0.6f))
                            }
                        }
                    }

                    // Rate Limit
                    Text(
                        stringResource(R.string.auto_reply_rate),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeColors.textPrimary
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, themeColors.borderColor, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    stringResource(R.string.auto_reply_max),
                                    fontSize = 14.sp,
                                    color = themeColors.textSecondary
                                )
                                Text(
                                    "${config.maxRepliesPerContactPerHour}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = themeColors.accentPurple
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = config.maxRepliesPerContactPerHour.toFloat(),
                                onValueChange = {
                                    saveConfig(config.copy(maxRepliesPerContactPerHour = it.toInt()))
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = themeColors.accentPurple,
                                    activeTrackColor = themeColors.accentPurple
                                )
                            )
                        }
                    }

                    AllowlistSection(config = config, onChange = { saveConfig(it) })

                    LongTextSection(
                        title = stringResource(R.string.auto_reply_about_you),
                        hint = stringResource(R.string.auto_reply_about_you_hint),
                        placeholder = stringResource(R.string.auto_reply_about_you_example),
                        value = config.personaNotes,
                        onChange = { saveConfig(config.copy(personaNotes = it), debounce = true) },
                    )

                    LongTextSection(
                        title = stringResource(R.string.auto_reply_how_you_write),
                        hint = stringResource(R.string.auto_reply_how_you_write_hint),
                        placeholder = stringResource(R.string.auto_reply_how_you_write_example),
                        value = config.styleNotes,
                        onChange = { saveConfig(config.copy(styleNotes = it), debounce = true) },
                    )

                    // Custom Prompt
                    Text(
                        stringResource(R.string.auto_reply_tone),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeColors.textPrimary
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, themeColors.borderColor, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = themeColors.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                stringResource(R.string.auto_reply_tone_label),
                                fontSize = 14.sp,
                                color = themeColors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = config.customPrompt ?: "",
                                onValueChange = {
                                    saveConfig(config.copy(customPrompt = it.ifBlank { null }), debounce = true)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.auto_reply_tone_example),
                                        color = themeColors.textSecondary.copy(alpha = 0.5f)
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = themeColors.accentPurple,
                                    unfocusedBorderColor = themeColors.borderColor,
                                    focusedTextColor = themeColors.textPrimary,
                                    unfocusedTextColor = themeColors.textPrimary,
                                    cursorColor = themeColors.accentPurple
                                ),
                                shape = RoundedCornerShape(12.dp),
                                maxLines = 3
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AppToggleRow(
    appName: String,
    emoji: String,
    isEnabled: Boolean,
    themeColors: com.opendroid.ai.ui.theme.OpenDroidColors,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(appName, fontSize = 15.sp, color = themeColors.textPrimary)
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = themeColors.accentNeonGreen,
                checkedTrackColor = themeColors.accentNeonGreen.copy(alpha = 0.5f)
            )
        )
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
            val cn = android.content.ComponentName.unflattenFromString(name)
            if (cn != null) {
                if (cn.packageName == pkgName) {
                    return true
                }
            }
        }
    }
    return false
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    if (com.opendroid.ai.accessibility.OpenDroidAccessibilityService.getInstance() != null) {
        return true
    }
    val expectedComponentName = android.content.ComponentName(context, com.opendroid.ai.accessibility.OpenDroidAccessibilityService::class.java).flattenToString()
    val enabledServicesSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
    return enabledServicesSetting.contains(expectedComponentName)
}

/**
 * The contacts auto-reply is allowed to answer, and who each of them is.
 *
 * The engine has always had an allowlist and nothing ever let anyone fill it
 * in, so switching auto-reply on meant replying to everyone who wrote. An
 * empty list now means nobody, and this is where it stops being empty.
 */
@Composable
private fun AllowlistSection(
    config: AutoReplyConfig,
    onChange: (AutoReplyConfig) -> Unit,
) {
    val colors = AppTheme.colors
    var newContact by remember { mutableStateOf("") }
    var newNote by remember { mutableStateOf("") }

    Text(stringResource(R.string.auto_reply_who), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (config.whitelistedContacts.isEmpty()) {
                    stringResource(R.string.auto_reply_nobody)
                } else {
                    stringResource(R.string.auto_reply_only_these)
                },
                fontSize = 12.sp,
                color = if (config.whitelistedContacts.isEmpty()) colors.accentOrange else colors.textSecondary,
            )

            config.whitelistedContacts.sorted().forEach { contact ->
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(contact, fontSize = 14.sp, color = colors.textPrimary)
                        val note = config.contactNotes[contact].orEmpty()
                        Text(
                            text = note.ifBlank { stringResource(R.string.auto_reply_no_note) },
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                        )
                    }
                    TextButton(onClick = {
                        onChange(
                            config.copy(
                                whitelistedContacts = config.whitelistedContacts - contact,
                                contactNotes = config.contactNotes.filterKeys { it != contact },
                            )
                        )
                    }) {
                        Text(stringResource(R.string.auto_reply_remove), fontSize = 12.sp, color = colors.accentRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.borderColor.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = newContact,
                onValueChange = { newContact = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.auto_reply_contact_label), fontSize = 12.sp) },
                placeholder = { Text(stringResource(R.string.auto_reply_contact_hint), color = colors.textSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentNeonGreen,
                    unfocusedBorderColor = colors.borderColor,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accentNeonGreen,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = newNote,
                onValueChange = { newNote = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.auto_reply_relation_label), fontSize = 12.sp) },
                placeholder = { Text(stringResource(R.string.auto_reply_relation_hint), color = colors.textSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentNeonGreen,
                    unfocusedBorderColor = colors.borderColor,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accentNeonGreen,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val name = newContact.trim()
                    if (name.isNotEmpty()) {
                        onChange(
                            config.copy(
                                whitelistedContacts = config.whitelistedContacts + name,
                                contactNotes = if (newNote.isBlank()) {
                                    config.contactNotes
                                } else {
                                    config.contactNotes + mapOf(name to newNote.trim())
                                },
                            )
                        )
                        newContact = ""
                        newNote = ""
                    }
                },
                enabled = newContact.isNotBlank(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentGreenButton,
                    contentColor = colors.background,
                ),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Text(stringResource(R.string.auto_reply_add_contact), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A titled multi-line field with a sentence explaining what it is for. */
@Composable
private fun LongTextSection(
    title: String,
    hint: String,
    placeholder: String,
    value: String?,
    onChange: (String?) -> Unit,
) {
    val colors = AppTheme.colors
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(hint, fontSize = 12.sp, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = value.orEmpty(),
                onValueChange = { onChange(it.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder, color = colors.textSecondary.copy(alpha = 0.5f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentNeonGreen,
                    unfocusedBorderColor = colors.borderColor,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accentNeonGreen,
                ),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
                maxLines = 6,
            )
        }
    }
}







