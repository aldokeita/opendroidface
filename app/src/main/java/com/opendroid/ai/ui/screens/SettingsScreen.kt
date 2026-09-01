package com.opendroid.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.opendroid.ai.core.llm.providers.CodexProvider
import androidx.compose.ui.res.stringResource
import com.opendroid.ai.R
import com.opendroid.ai.core.llm.codex.CodexAccountState
import androidx.compose.ui.text.style.TextAlign
import com.opendroid.ai.core.language.AppLanguage
import com.opendroid.ai.core.voice.SpokenLanguage
import com.opendroid.ai.core.voice.TtsVoicePreview
import com.opendroid.ai.core.voice.voiceDisplayLabel
import com.opendroid.ai.ui.face.rememberAppLanguageStore
import com.opendroid.ai.ui.face.rememberSpeechOutputStore
import com.opendroid.ai.ui.face.rememberTtsVoiceStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.opendroid.ai.data.models.AutoMode
import com.opendroid.ai.data.models.LLMConfig
import com.opendroid.ai.data.models.effectiveGrantedActions
import com.opendroid.ai.data.models.resolvedAutoMode
import com.opendroid.ai.core.llm.OnDeviceModelRegistry
import com.opendroid.ai.core.llm.OnDeviceBackend
import com.opendroid.ai.core.llm.ConnectionTestState
import com.opendroid.ai.core.llm.error.LLMError
import com.opendroid.ai.core.security.ProviderCredentialRecoveryState
import com.opendroid.ai.data.repository.ProviderCredentialPersistenceState
import com.google.mlkit.genai.prompt.*
import com.google.mlkit.genai.common.FeatureStatus
import com.opendroid.ai.ui.theme.*
import com.opendroid.ai.ui.viewmodel.SettingsViewModel
import com.opendroid.ai.data.db.entities.ModelEntity
import com.opendroid.ai.data.db.entities.ModelStatus
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToBenchmark: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit = {},
    onNavigateToTermsOfUse: () -> Unit = {},
    onNavigateToHelpCenter: () -> Unit = {},
    onNavigateToLicense: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToAutoReply: () -> Unit = {},
    onNavigateToNotificationHistory: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToCrashLog: () -> Unit = {},
    onNavigateToRoutines: () -> Unit = {},
    // Plan, Macros and Logs were tabs until the bar was cut to three. They are
    // things you go and look at, so they are reached from here.
    onNavigateToPlan: () -> Unit = {},
    onNavigateToMacros: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val config by viewModel.llmConfig.collectAsState()
    val connectionResults by viewModel.connectionResults.collectAsState()
    val dbModels by viewModel.allModels.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val hfToken by viewModel.huggingFaceToken.collectAsState()
    val providerCredentialRecoveryState by viewModel.providerCredentialRecoveryState.collectAsState()
    val providerCredentialPersistenceState by viewModel.providerCredentialPersistenceState.collectAsState()
    val colors = LocalOpenDroidColors.current

    val providers = listOf(
        "Google Gemini",
        "OpenAI",
        "Anthropic Claude",
        "Groq",
        "Mistral AI",
        "OpenRouter",
        "Together AI",
        "Cohere",
        "DeepSeek",
        "Copilot API",
        CodexProvider.PROVIDER_NAME,
        "Custom OpenAI Compatible",
        "Ollama",
        "On-Device AI"
    )

    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var keysSectionExpanded by remember { mutableStateOf(false) }
    var voiceSectionExpanded by remember { mutableStateOf(false) }
    // Planning fallbacks: eleven checkboxes that sat between the model field and
    // everything else, on a screen whose first job is getting one provider
    // working. Folded away by default.
    var fallbacksExpanded by remember { mutableStateOf(false) }
    var planningSectionExpanded by remember { mutableStateOf(false) }

    var showAuthRequiredDialog by remember { mutableStateOf<String?>(null) }
    var licenseUrlForDialog by remember { mutableStateOf("") }
    var showCellularWarningDialog by remember { mutableStateOf<String?>(null) }
    var pendingCellularResumeModelId by remember { mutableStateOf<String?>(null) }
    var activeImportModelId by remember { mutableStateOf<String?>(null) }
    var importAsCustomModel by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            when {
                importAsCustomModel -> viewModel.importCustomLocalModel(uri)
                activeImportModelId != null -> viewModel.importLocalModel(activeImportModelId!!, uri)
            }
        }
        activeImportModelId = null
        importAsCustomModel = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 19.sp,
                        letterSpacing = (-0.3).sp
                    )
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
                .consumeWindowInsets(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            if (providerCredentialRecoveryState == ProviderCredentialRecoveryState.CredentialsMustBeReentered) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.accentOrange.copy(alpha = 0.12f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_creds_reenter),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentOrange
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.settings_creds_unreadable),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = viewModel::resetProviderCredentialsForReentry,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange)
                            ) {
                                Text(stringResource(R.string.settings_creds_clear), color = colors.background)
                            }
                        }
                    }
                }
            }

            if (providerCredentialPersistenceState ==
                ProviderCredentialPersistenceState.StorageUnavailable
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.accentOrange.copy(alpha = 0.12f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_creds_unsaved),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentOrange
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.settings_creds_storage_down),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Active LLM Provider Selection Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // One card for the whole setup path, in the order it is
                        // done: who answers, which model, and the key that lets
                        // it. These were three separate places on the page.
                        Text(
                            text = stringResource(R.string.settings_brain),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_brain_subtitle),
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.settings_provider),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Dropdown menu trigger
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.background)
                                .clickable { providerDropdownExpanded = true }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (config.activeProvider == "On-Device AI" || config.activeProvider == "Gemma 4 (On-device)") "On-Device AI" else config.activeProvider,
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = stringResource(R.string.settings_dropdown),
                                    tint = colors.accentNeonGreen
                                )
                            }

                            DropdownMenu(
                                expanded = providerDropdownExpanded,
                                onDismissRequest = { providerDropdownExpanded = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .background(colors.cardBackground)
                            ) {
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = stringResource(R.string.settings_offline_ai),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary
                                        ) 
                                    },
                                    enabled = false,
                                    onClick = {}
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_on_device), color = colors.textPrimary, modifier = Modifier.padding(start = 8.dp)) },
                                    onClick = {
                                        viewModel.updateActiveProvider("On-Device AI")
                                        providerDropdownExpanded = false
                                    }
                                )
                                
                                Divider(color = colors.borderColor, thickness = 1.dp)

                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = stringResource(R.string.settings_cloud_ai),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary
                                        ) 
                                    },
                                    enabled = false,
                                    onClick = {}
                                )
                                val cloudProvidersList = providers.filter { it != "On-Device AI" }
                                cloudProvidersList.forEach { name ->
                                    val displayName = when (name) {
                                        "Google Gemini" -> "Gemini"
                                        "Anthropic Claude" -> "Claude"
                                        else -> name
                                    }
                                    DropdownMenuItem(
                                        text = { Text(displayName, color = colors.textPrimary, modifier = Modifier.padding(start = 8.dp)) },
                                        onClick = {
                                            viewModel.updateActiveProvider(name)
                                            providerDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        val modelsLoading by viewModel.modelsLoading.collectAsState()
                        val modelFetchNotice by viewModel.modelFetchNotice.collectAsState()
                        val fetchedModels = config.modelCache[config.activeProvider] ?: emptyList()
                        var modelDropdownExpanded by remember { mutableStateOf(false) }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_model),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            if (modelsLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = colors.accentNeonGreen,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = { viewModel.refreshModels(force = true) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.settings_refresh_models),
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Filled, borderless, no floating label - the same
                            // control as the provider box directly above it. It was
                            // an outlined field with a notched label, so two
                            // stacked controls doing the same job spoke two
                            // different form languages. It stays a text field
                            // rather than a pure dropdown because a model name can
                            // be typed in for providers that serve one this build
                            // has never heard of.
                            TextField(
                                value = config.activeModel,
                                onValueChange = { viewModel.updateActiveModel(it) },
                                placeholder = {
                                    Text(stringResource(R.string.settings_model_name), color = colors.textSecondary, fontSize = 15.sp)
                                },
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { modelDropdownExpanded = !modelDropdownExpanded }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = stringResource(R.string.settings_show_models),
                                            tint = colors.accentNeonGreen
                                        )
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = colors.background,
                                    unfocusedContainerColor = colors.background,
                                    disabledContainerColor = colors.background,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                    cursorColor = colors.accentNeonGreen,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            )

                        if (fetchedModels.isNotEmpty()) {
                                DropdownMenu(
                                    expanded = modelDropdownExpanded,
                                    onDismissRequest = { modelDropdownExpanded = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .background(colors.cardBackground)
                                ) {
                                    fetchedModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = model.displayName,
                                                        color = colors.textPrimary,
                                                        fontSize = 14.sp
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (model.isRecommended) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(
                                                                        colors.accentNeonGreen.copy(alpha = 0.15f),
                                                                        RoundedCornerShape(4.dp)
                                                                    )
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "REC",
                                                                    color = colors.accentNeonGreen,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                        if (model.isFree) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(
                                                                        colors.accentCyan.copy(alpha = 0.15f),
                                                                        RoundedCornerShape(4.dp)
                                                                    )
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "FREE",
                                                                    color = colors.accentCyan,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                        if (model.isPremium) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(
                                                                        colors.accentOrange.copy(alpha = 0.15f),
                                                                        RoundedCornerShape(4.dp)
                                                                    )
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "PRO",
                                                                    color = colors.accentOrange,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            onClick = {
                                                viewModel.updateActiveModel(model.id)
                                                modelDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Model names are never bundled with the app, so when the
                        // live list is unavailable the reason is shown rather than
                        // a list that quietly went out of date. Directly under the
                        // model field: it was at the bottom of the card, four
                        // controls away from the thing it is about.
                        modelFetchNotice?.let { notice ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = notice,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = colors.accentRed,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // The key for the provider that was just chosen, in the
                        // same card. It used to live in a collapsed section far
                        // down the page, so setting a provider up meant picking it
                        // here and then scrolling past everything else to find
                        // where to paste the key - three separate places for one
                        // job. The other providers' keys are still down there.
                        // Codex is absent from this: it signs in instead, and
                        // its section is below.
                        val providerNeedsKey = config.activeProvider != "Ollama" &&
                            config.activeProvider != "On-Device AI" &&
                            config.activeProvider != CodexProvider.PROVIDER_NAME
                        if (providerNeedsKey) {
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = stringResource(R.string.settings_api_key),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SecureApiKeyField(
                                value = config.apiKeys[config.activeProvider] ?: "",
                                onValueChange = { viewModel.updateApiKey(config.activeProvider, it) },
                                label = "${config.activeProvider} API key"
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = connectionStatusLabel(connectionResults[config.activeProvider]),
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.testConnection(config.activeProvider) }) {
                                    Text(
                                        stringResource(R.string.settings_test_connection),
                                        fontSize = 12.sp,
                                        color = colors.accentNeonGreen
                                    )
                                }
                            }
                        }

                        if (config.activeProvider == CodexProvider.PROVIDER_NAME) {
                            CodexSignIn(viewModel = viewModel)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = colors.borderColor.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { fallbacksExpanded = !fallbacksExpanded }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_planning_fallbacks),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Icon(
                                imageVector = if (fallbacksExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = colors.textSecondary
                            )
                        }
                        AnimatedVisibility(visible = fallbacksExpanded) {
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_fallback_hint),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                providers
                                    .filter { it != config.activeProvider && it != "On-Device AI" }
                                    .forEach { fallbackProvider ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = config.fallbackProviders.contains(fallbackProvider),
                                                onCheckedChange = { enabled ->
                                                    viewModel.updateFallbackProvider(fallbackProvider, enabled)
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = colors.accentNeonGreen,
                                                    uncheckedColor = colors.borderColor,
                                                    checkmarkColor = colors.background
                                                )
                                            )
                                            Text(
                                                text = fallbackProvider,
                                                color = colors.textPrimary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                            }
                        }

                    }
                }
            }

            // Benchmark latency report card link
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToBenchmark() },
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = stringResource(R.string.settings_benchmark_short),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_benchmark),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_benchmark_hint),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Ollama Endpoint Config Card (Visible only when Ollama is selected)
            if (config.activeProvider == "Ollama") {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_ollama_endpoint),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = config.ollamaUrl,
                                onValueChange = { viewModel.updateOllamaUrl(it) },
                                label = { Text(stringResource(R.string.settings_ollama_url), fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentNeonGreen,
                                    unfocusedBorderColor = colors.borderColor,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.settings_lan_hint_ollama),
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // On-Device AI Status Card (Visible when On-Device AI or legacy Gemma provider is selected)
            if (config.activeProvider == "On-Device AI" || config.activeProvider == "Gemma 4 (On-device)") {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_on_device_status),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Show which model is active
                            val activeSpec = OnDeviceModelRegistry.findById(config.activeModel)
                            Text(
                                text = "Active: ${activeSpec?.displayName ?: config.activeModel}",
                                fontSize = 12.sp,
                                color = colors.accentCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (activeSpec != null) {
                                Text(
                                    text = "Backend: ${if (activeSpec.backend == OnDeviceBackend.AI_CORE) "Android AI Core" else "LiteRT-LM"}",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // ─── AI Core Backend Section ───
                            Text(
                                text = stringResource(R.string.settings_ai_core),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentCyan
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            var gemma4Status by remember { mutableStateOf("Checking...") }
                            var showGemma4Download by remember { mutableStateOf(false) }
                            var gemma3nStatus by remember { mutableStateOf("Checking...") }
                            var showGemma3nDownload by remember { mutableStateOf(false) }
                            
                            LaunchedEffect(Unit) {
                                // Check Gemma 4 (default/stable)
                                try {
                                    val client = Generation.getClient()
                                    val status = client.checkStatus()
                                    gemma4Status = when (status) {
                                        FeatureStatus.AVAILABLE -> "Available and ready"
                                        FeatureStatus.DOWNLOADABLE -> {
                                            showGemma4Download = true
                                            "Download needed"
                                        }
                                        FeatureStatus.DOWNLOADING -> "Downloading..."
                                        FeatureStatus.UNAVAILABLE -> "Not supported on this device"
                                        else -> "Unknown"
                                    }
                                } catch (e: Exception) {
                                    gemma4Status = "Not supported on this device"
                                }
                                
                                // Check Gemma 3n (preview/fast)
                                try {
                                    val previewConfig = generationConfig {
                                        modelConfig = modelConfig {
                                            releaseStage = ModelReleaseStage.PREVIEW
                                            preference = ModelPreference.FAST
                                        }
                                    }
                                    val client3n = Generation.getClient(previewConfig)
                                    val status3n = client3n.checkStatus()
                                    gemma3nStatus = when (status3n) {
                                        FeatureStatus.AVAILABLE -> "Available and ready"
                                        FeatureStatus.DOWNLOADABLE -> {
                                            showGemma3nDownload = true
                                            "Download needed"
                                        }
                                        FeatureStatus.DOWNLOADING -> "Downloading..."
                                        FeatureStatus.UNAVAILABLE -> "Not supported on this device"
                                        else -> "Unknown"
                                    }
                                } catch (e: Exception) {
                                    gemma3nStatus = "Not supported on this device"
                                }
                            }
                            
                            // Gemma 4 AI Core row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Gemma 4", fontSize = 13.sp, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = gemma4Status,
                                    fontSize = 11.sp,
                                    color = if (gemma4Status.contains("ready")) colors.accentNeonGreen else colors.textSecondary
                                )
                            }
                            if (showGemma4Download) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                val client = Generation.getClient()
                                                gemma4Status = "Downloading..."
                                                showGemma4Download = false
                                                client.download().collect { }
                                                gemma4Status = "Download complete"
                                            } catch (e: Exception) {
                                                gemma4Status = "Download failed: ${e.localizedMessage}"
                                                showGemma4Download = true
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentNeonGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.settings_dl_gemma4), color = colors.background)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Gemma 3n AI Core row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Gemma 3n Multimodal", fontSize = 13.sp, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = gemma3nStatus,
                                    fontSize = 11.sp,
                                    color = if (gemma3nStatus.contains("ready")) colors.accentNeonGreen else colors.textSecondary
                                )
                            }
                            if (showGemma3nDownload) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                val previewConfig = generationConfig {
                                                    modelConfig = modelConfig {
                                                        releaseStage = ModelReleaseStage.PREVIEW
                                                        preference = ModelPreference.FAST
                                                    }
                                                }
                                                val client3n = Generation.getClient(previewConfig)
                                                gemma3nStatus = "Downloading..."
                                                showGemma3nDownload = false
                                                client3n.download().collect { }
                                                gemma3nStatus = "Download complete"
                                            } catch (e: Exception) {
                                                gemma3nStatus = "Download failed: ${e.localizedMessage}"
                                                showGemma3nDownload = true
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.settings_dl_gemma3n), color = colors.background)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = colors.borderColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // ─── Hugging Face Section ───
                            Text(
                                text = stringResource(R.string.settings_hf_section),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentOrange
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_hf_hint),
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = colors.cardBackground.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val validationStatus by viewModel.huggingFaceValidationStatus.collectAsState()
                                    val lastVerified by viewModel.huggingFaceLastVerified.collectAsState()
                                    var showToken by remember { mutableStateOf(false) }

                                    OutlinedTextField(
                                        value = hfToken,
                                        onValueChange = { viewModel.updateHuggingFaceToken(it) },
                                        label = { Text(stringResource(R.string.settings_hf_token), fontSize = 12.sp) },
                                        singleLine = true,
                                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                        placeholder = { Text("hf_...", fontSize = 12.sp, color = colors.textSecondary) },
                                        trailingIcon = {
                                            IconButton(onClick = { showToken = !showToken }) {
                                                Icon(
                                                    imageVector = if (showToken) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = stringResource(R.string.settings_toggle_token),
                                                    tint = colors.textSecondary
                                                )
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.accentOrange,
                                            unfocusedBorderColor = colors.borderColor,
                                            focusedTextColor = colors.textPrimary,
                                            unfocusedTextColor = colors.textPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val context = LocalContext.current
                                        val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(
                                                onClick = {
                                                    val clip = clipboardManager.primaryClip
                                                    if (clip != null && clip.itemCount > 0) {
                                                        val pasted = clip.getItemAt(0).text?.toString() ?: ""
                                                        viewModel.updateHuggingFaceToken(pasted)
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(stringResource(R.string.common_paste), fontSize = 11.sp, color = colors.accentOrange)
                                            }

                                            TextButton(
                                                onClick = { viewModel.updateHuggingFaceToken("") },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(stringResource(R.string.common_clear), fontSize = 11.sp, color = colors.accentRed)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Status display
                                    val statusDisplay = when (validationStatus) {
                                        "Valid" -> "✓ Token Valid"
                                        "Invalid" -> "✗ Invalid Token"
                                        "Verifying..." -> "Checking token..."
                                        "Unable to verify" -> "Unable to verify token."
                                        else -> "⚠ Token Required"
                                    }

                                    val statusColor = when (validationStatus) {
                                        "Valid" -> colors.accentNeonGreen
                                        "Invalid" -> colors.accentRed
                                        "Verifying..." -> colors.accentCyan
                                        "Unable to verify" -> colors.accentOrange
                                        else -> colors.textSecondary
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Status: $statusDisplay", fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Bold)
                                            Text("Last Verified: $lastVerified", fontSize = 9.sp, color = colors.textSecondary)
                                            Text(stringResource(R.string.settings_storage_encrypted), fontSize = 9.sp, color = colors.textSecondary)
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { viewModel.validateHuggingFaceToken() },
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text(stringResource(R.string.settings_hf_validate), fontSize = 10.sp, color = colors.background, fontWeight = FontWeight.Bold)
                                            }

                                            if (hfToken.isNotBlank()) {
                                                Button(
                                                    onClick = { viewModel.removeHuggingFaceToken() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentRed.copy(alpha = 0.2f)),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text(stringResource(R.string.settings_hf_remove), fontSize = 10.sp, color = colors.accentRed, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = colors.borderColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // ─── LiteRT-LM Backend Section ───
                            Text(
                                text = stringResource(R.string.settings_litert),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentOrange
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_litert_hint),
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Dynamically list all LiteRT-LM models from database
                            val liteRTModels = OnDeviceModelRegistry.liteRTOnly
                            liteRTModels.forEach { spec ->
                                val modelEntity = dbModels.find { it.id == spec.id }
                                val status = modelEntity?.status ?: ModelStatus.NOT_DOWNLOADED
                                val progress = modelEntity?.downloadProgress ?: 0
                                val downloadedSize = modelEntity?.downloadedSize ?: 0L
                                val totalSize = modelEntity?.size ?: spec.expectedSize
                                val speed = modelEntity?.downloadSpeed ?: ""
                                val eta = modelEntity?.etaString ?: ""
                                
                                var expanded by remember { mutableStateOf(false) }
                                val isApiCompatible = android.os.Build.VERSION.SDK_INT >= spec.minSdk
                                val managedDownloadAvailable = spec.isManagedDownloadAvailable
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        // The only outline left on this screen, because
                                        // here it says which model is live rather than
                                        // just drawing a box around a row.
                                        .then(
                                            if (config.activeModel == spec.id) {
                                                Modifier.border(
                                                    1.dp,
                                                    colors.accentNeonGreen.copy(alpha = 0.5f),
                                                    RoundedCornerShape(10.dp),
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable { if (isApiCompatible) expanded = !expanded },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (config.activeModel == spec.id) {
                                            colors.accentNeonGreen.copy(alpha = 0.08f)
                                        } else {
                                            colors.background
                                        }
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = spec.displayName,
                                                        fontSize = 13.sp,
                                                        color = if (isApiCompatible) colors.textPrimary else colors.textSecondary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    if (spec.isRecommended) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(colors.accentOrange.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "REC",
                                                                color = colors.accentOrange,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                if (spec.authRequired) colors.accentOrange.copy(alpha = 0.12f)
                                                                else colors.accentCyan.copy(alpha = 0.12f),
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = if (spec.authRequired) "GATED · HF TOKEN" else "PUBLIC · NO TOKEN",
                                                            color = if (spec.authRequired) colors.accentOrange else colors.accentCyan,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = if (managedDownloadAvailable) {
                                                        if (spec.authRequired) {
                                                            "Backend: LiteRT-LM · Gated Hugging Face download"
                                                        } else {
                                                            "Backend: LiteRT-LM · Public download (no token)"
                                                        }
                                                    } else {
                                                        "Backend: LiteRT-LM · In-app download unavailable; local import only"
                                                    },
                                                    fontSize = 10.sp,
                                                    color = colors.textSecondary
                                                )
                                            }
                                            
                                            val badgeColor = when (status) {
                                                ModelStatus.READY -> colors.accentNeonGreen
                                                ModelStatus.DOWNLOADING -> colors.accentOrange
                                                ModelStatus.PAUSED -> colors.accentOrange
                                                ModelStatus.LOADING -> colors.accentCyan
                                                ModelStatus.FAILED -> colors.accentRed
                                                else -> colors.textSecondary
                                            }
                                            
                                            val statusText = when {
                                                !isApiCompatible -> "API ${spec.minSdk}+ Req"
                                                status == ModelStatus.READY -> "Downloaded"
                                                !managedDownloadAvailable -> "In-app unavailable"
                                                status == ModelStatus.DOWNLOADING -> "${progress}%"
                                                status == ModelStatus.PAUSED -> "Paused"
                                                status == ModelStatus.LOADING -> "Loading..."
                                                status == ModelStatus.FAILED -> "Failed"
                                                else -> "Not Downloaded"
                                            }
                                            
                                            Text(
                                                text = statusText,
                                                fontSize = 10.sp,
                                                color = badgeColor,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                        
                                        if (isApiCompatible && (status == ModelStatus.DOWNLOADING || status == ModelStatus.PAUSED)) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LinearProgressIndicator(
                                                progress = { progress.toFloat() / 100f },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = colors.accentOrange,
                                                trackColor = colors.borderColor
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${formatBytes(downloadedSize)} / ${formatBytes(totalSize)}" +
                                                           (if (status == ModelStatus.DOWNLOADING && speed.isNotEmpty()) " @ $speed" else ""),
                                                    fontSize = 9.sp,
                                                    color = colors.textSecondary
                                                )
                                                if (status == ModelStatus.DOWNLOADING && eta.isNotEmpty()) {
                                                    Text(
                                                        text = "ETA: $eta",
                                                        fontSize = 9.sp,
                                                        color = colors.textSecondary
                                                    )
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                if (status == ModelStatus.DOWNLOADING) {
                                                    Button(
                                                        onClick = { viewModel.pauseDownload(spec.id) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = colors.borderColor),
                                                        modifier = Modifier.height(28.dp).padding(horizontal = 4.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.common_pause), modifier = Modifier.size(12.dp), tint = colors.textPrimary)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(stringResource(R.string.common_pause), fontSize = 10.sp, color = colors.textPrimary)
                                                    }
                                                } else if (status == ModelStatus.PAUSED) {
                                                    Button(
                                                        onClick = {
                                                            if (viewModel.isCellularNetwork()) {
                                                                pendingCellularResumeModelId = spec.id
                                                            } else {
                                                                viewModel.resumeDownload(spec.id)
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange),
                                                        modifier = Modifier.height(28.dp).padding(horizontal = 4.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.common_resume), modifier = Modifier.size(12.dp), tint = colors.background)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(stringResource(R.string.common_resume), fontSize = 10.sp, color = colors.background)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Button(
                                                    onClick = { viewModel.cancelDownload(spec.id) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentRed.copy(alpha = 0.6f)),
                                                    modifier = Modifier.height(28.dp).padding(horizontal = 4.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(stringResource(R.string.common_cancel), fontSize = 10.sp, color = Color.White)
                                                }
                                            }
                                        }

                                        if (isApiCompatible && status == ModelStatus.FAILED) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            val errorText = modelEntity?.etaString ?: "Download failed"
                                            Text(
                                                text = errorText,
                                                fontSize = 10.sp,
                                                color = colors.accentRed,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (spec.licenseUrl.isNotEmpty() && (errorText.contains("permission", ignoreCase = true) || errorText.contains("license", ignoreCase = true))) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                                Button(
                                                    onClick = { uriHandler.openUri(spec.licenseUrl) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange.copy(alpha = 0.2f)),
                                                    modifier = Modifier.fillMaxWidth().height(28.dp),
                                                    contentPadding = PaddingValues(vertical = 2.dp)
                                                ) {
                                                    Text(stringResource(R.string.settings_open_model_page), color = colors.accentOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        
                                        AnimatedVisibility(visible = expanded) {
                                            Column {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Divider(color = colors.borderColor, thickness = 0.5.dp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (status == ModelStatus.NOT_DOWNLOADED || status == ModelStatus.FAILED) {
                                                        if (managedDownloadAvailable) {
                                                            Button(
                                                                onClick = {
                                                                    val hfTokenVal = hfToken
                                                                    if (spec.authRequired && hfTokenVal.isBlank()) {
                                                                        showAuthRequiredDialog = spec.displayName
                                                                        licenseUrlForDialog = spec.licenseUrl
                                                                    } else if (viewModel.isCellularNetwork()) {
                                                                        showCellularWarningDialog = spec.id
                                                                    } else {
                                                                        viewModel.downloadModel(spec.id)
                                                                    }
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange),
                                                                modifier = Modifier.weight(1f).height(32.dp),
                                                                contentPadding = PaddingValues(horizontal = 4.dp)
                                                            ) {
                                                                Text(stringResource(R.string.common_download), fontSize = 11.sp, color = colors.background)
                                                            }
                                                        }

                                                        Button(
                                                            onClick = {
                                                                importAsCustomModel = false
                                                                activeImportModelId = spec.id
                                                                importLauncher.launch("*/*")
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = colors.borderColor),
                                                            modifier = Modifier.weight(1f).height(32.dp),
                                                            contentPadding = PaddingValues(horizontal = 4.dp)
                                                        ) {
                                                            Text(stringResource(R.string.common_import), fontSize = 11.sp, color = colors.textPrimary)
                                                        }
                                                    }
                                                    
                                                    if (status == ModelStatus.READY) {
                                                         Button(
                                                             onClick = { viewModel.loadModel(spec.id) },
                                                             colors = ButtonDefaults.buttonColors(
                                                                 containerColor = if (config.activeModel == spec.id) colors.accentNeonGreen else colors.accentCyan
                                                             ),
                                                             modifier = Modifier.weight(1f).height(32.dp),
                                                             contentPadding = PaddingValues(horizontal = 4.dp)
                                                         ) {
                                                             Icon(
                                                                 if (config.activeModel == spec.id) Icons.Default.Check else Icons.Default.ArrowForward,
                                                                 contentDescription = null,
                                                                 modifier = Modifier.size(12.dp),
                                                                 tint = colors.background
                                                             )
                                                             Spacer(modifier = Modifier.width(4.dp))
                                                             Text(if (config.activeModel == spec.id) "Active" else "Load Model", fontSize = 11.sp, color = colors.background)
                                                         }
                                                         
                                                         Button(
                                                             onClick = { viewModel.deleteModel(spec.id) },
                                                             colors = ButtonDefaults.buttonColors(containerColor = colors.accentRed.copy(alpha = 0.2f)),
                                                             modifier = Modifier.height(32.dp),
                                                             contentPadding = PaddingValues(horizontal = 8.dp)
                                                         ) {
                                                             Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), modifier = Modifier.size(14.dp), tint = colors.accentRed)
                                                         }
                                                    }
                                                    
                                                    Button(
                                                        onClick = {
                                                            // Info clicked (No-op or log details)
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = colors.borderColor),
                                                        modifier = Modifier.height(32.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                                    ) {
                                                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.common_info), modifier = Modifier.size(14.dp), tint = colors.textSecondary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ─── Custom LiteRT imports ───
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = colors.borderColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.settings_custom_litert),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentOrange
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_import_hint),
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    importAsCustomModel = true
                                    activeImportModelId = null
                                    importLauncher.launch("*/*")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text(stringResource(R.string.settings_import_litert), fontSize = 12.sp, color = colors.background)
                            }

                            val customModels = dbModels.filter {
                                OnDeviceModelRegistry.isCustomId(it.id) &&
                                    it.status == ModelStatus.READY
                            }
                            customModels.forEach { entity ->
                                var expanded by remember(entity.id) { mutableStateOf(false) }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        // The only outline left on this screen, because
                                        // here it says which model is live rather than
                                        // just drawing a box around a row.
                                        .then(
                                            if (config.activeModel == entity.id) {
                                                Modifier.border(
                                                    1.dp,
                                                    colors.accentNeonGreen.copy(alpha = 0.5f),
                                                    RoundedCornerShape(10.dp),
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable { expanded = !expanded },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (config.activeModel == entity.id) {
                                            colors.accentNeonGreen.copy(alpha = 0.08f)
                                        } else {
                                            colors.background
                                        }
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entity.name,
                                                    fontSize = 13.sp,
                                                    color = colors.textPrimary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Custom LiteRT · ${formatBytes(entity.size)} · no token",
                                                    fontSize = 10.sp,
                                                    color = colors.textSecondary
                                                )
                                            }
                                            Text(
                                                text = if (config.activeModel == entity.id) "Active" else "Ready",
                                                fontSize = 10.sp,
                                                color = colors.accentNeonGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        AnimatedVisibility(visible = expanded) {
                                            Column {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Divider(color = colors.borderColor, thickness = 0.5.dp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = { viewModel.loadModel(entity.id) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (config.activeModel == entity.id) {
                                                                colors.accentNeonGreen
                                                            } else {
                                                                colors.accentCyan
                                                            }
                                                        ),
                                                        modifier = Modifier.weight(1f).height(32.dp),
                                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                                    ) {
                                                        Text(
                                                            if (config.activeModel == entity.id) "Active" else "Load Model",
                                                            fontSize = 11.sp,
                                                            color = colors.background
                                                        )
                                                    }
                                                    Button(
                                                        onClick = { viewModel.deleteModel(entity.id) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = colors.accentRed.copy(alpha = 0.2f)
                                                        ),
                                                        modifier = Modifier.height(32.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = stringResource(R.string.common_delete),
                                                            modifier = Modifier.size(14.dp),
                                                            tint = colors.accentRed
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = colors.borderColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // ─── Storage Cleanup Section ───
                            Text(
                                text = stringResource(R.string.settings_storage_cleanup),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentOrange
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            val totalSpace = storageInfo.totalBytes
                            val freeSpace = storageInfo.freeBytes
                            val usedByApp = storageInfo.usedByAppBytes
                            val usedPercentage = if (totalSpace > 0) ((totalSpace - freeSpace).toFloat() / totalSpace.toFloat()) else 0f
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Used: ${formatBytes(totalSpace - freeSpace)} / ${formatBytes(totalSpace)}",
                                    fontSize = 11.sp,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "${((totalSpace - freeSpace) * 100 / (totalSpace.coerceAtLeast(1L)))}% Used",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                             }
                             Spacer(modifier = Modifier.height(4.dp))
                             LinearProgressIndicator(
                                 progress = { usedPercentage },
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .height(6.dp)
                                     .clip(RoundedCornerShape(3.dp)),
                                 color = colors.accentOrange,
                                 trackColor = colors.borderColor
                             )
                             Spacer(modifier = Modifier.height(6.dp))
                             Text(
                                 text = "OpenDroid models occupy ${formatBytes(usedByApp)} of on-device storage.",
                                 fontSize = 10.sp,
                                 color = colors.textSecondary
                             )
                             Spacer(modifier = Modifier.height(8.dp))
                             Button(
                                 onClick = { viewModel.deleteUnusedModels() },
                                 colors = ButtonDefaults.buttonColors(containerColor = colors.accentRed.copy(alpha = 0.8f)),
                                 modifier = Modifier.fillMaxWidth(),
                                 shape = RoundedCornerShape(8.dp)
                             ) {
                                 Text(stringResource(R.string.settings_delete_unused), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                             }
                        }
                    }
                }
            }

            // Copilot Endpoint Config Card (Visible only when Copilot API is selected)
            if (config.activeProvider == "Copilot API") {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_copilot_endpoint),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = config.copilotUrl,
                                onValueChange = { viewModel.updateCopilotUrl(it) },
                                label = { Text(stringResource(R.string.settings_copilot_url), fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentNeonGreen,
                                    unfocusedBorderColor = colors.borderColor,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.settings_lan_hint_copilot),
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Custom OpenAI Compatible Endpoint Config Card (Visible only when Custom OpenAI Compatible is selected)
            if (config.activeProvider == "Custom OpenAI Compatible") {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_custom_openai),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = config.customEndpoints["Custom OpenAI Compatible"] ?: "",
                                onValueChange = { viewModel.updateCustomEndpoint("Custom OpenAI Compatible", it) },
                                label = { Text(stringResource(R.string.settings_base_url), fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentNeonGreen,
                                    unfocusedBorderColor = colors.borderColor,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.settings_base_url_hint),
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Provider API Keys Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { keysSectionExpanded = !keysSectionExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_keys_other),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Icon(
                                imageVector = if (keysSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.settings_toggle_keys),
                                tint = colors.textSecondary
                            )
                        }

                        AnimatedVisibility(visible = keysSectionExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // The active provider's key is in the Brain card at
                                // the top, where it is needed. This section is for
                                // the ones you are not using yet - keys pasted
                                // ahead of switching.
                                val inputProviders = providers.filter {
                                    it != "Ollama" && it != "On-Device AI" && it != config.activeProvider
                                }
                                inputProviders.forEach { providerName ->
                                    val keyVal = config.apiKeys[providerName] ?: ""
                                    val connectionState = connectionResults[providerName]
                                    SecureApiKeyField(
                                        value = keyVal,
                                        onValueChange = { viewModel.updateApiKey(providerName, it) },
                                        label = "$providerName API Key"
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = connectionStatusLabel(connectionState),
                                            fontSize = 10.sp,
                                            color = colors.textSecondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = { viewModel.testConnection(providerName) }
                                        ) {
                                            Text(stringResource(R.string.settings_test_connection), fontSize = 11.sp)
                                        }
                                    }
                                    Text(
                                        text = "Sends one minimal request to $providerName; provider charges may apply.",
                                        fontSize = 10.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ElevenLabs Voice Synthesis Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { voiceSectionExpanded = !voiceSectionExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_voice),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Icon(
                                imageVector = if (voiceSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.settings_toggle_voice),
                                tint = colors.textSecondary
                            )
                        }

                        AnimatedVisibility(visible = voiceSectionExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppLanguageRow()
                                SpeakTypedRepliesRow()
                                IndonesianVoicePicker()
                                Text(
                                    text = stringResource(R.string.settings_elevenlabs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                                SecureApiKeyField(
                                    value = config.elevenLabsApiKey,
                                    onValueChange = { viewModel.updateElevenLabsApiKey(it) },
                                    label = stringResource(R.string.settings_elevenlabs_key)
                                )
                                OutlinedTextField(
                                    value = config.elevenLabsVoiceId,
                                    onValueChange = { viewModel.updateElevenLabsVoiceId(it) },
                                    label = { Text(stringResource(R.string.settings_elevenlabs_voice), fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accentNeonGreen,
                                        unfocusedBorderColor = colors.borderColor,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = stringResource(R.string.settings_elevenlabs_hint),
                                    fontSize = 10.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Planning & Automation Preferences Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { planningSectionExpanded = !planningSectionExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_planning_section),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Icon(
                                imageVector = if (planningSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.settings_toggle_planning),
                                tint = colors.textSecondary
                            )
                        }

                        AnimatedVisibility(visible = planningSectionExpanded) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {

                        var showYoloWarning by remember { mutableStateOf(false) }
                        val autoMode = config.resolvedAutoMode()

                        Text(
                            text = stringResource(R.string.settings_auto_mode),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_auto_mode_hint),
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AutoMode.entries.forEach { mode ->
                                val selected = autoMode == mode
                                val accent = if (mode == AutoMode.YOLO) colors.accentRed else colors.accentNeonGreen
                                OutlinedButton(
                                    onClick = {
                                        if (mode == AutoMode.YOLO && !selected) showYoloWarning = true
                                        else viewModel.setAutoMode(mode)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (selected) accent else colors.textSecondary
                                    ),
                                    border = BorderStroke(1.dp, if (selected) accent else colors.borderColor),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = when (mode) {
                                            AutoMode.OFF -> "Off"
                                            AutoMode.AUTO -> "Auto"
                                            AutoMode.YOLO -> "YOLO"
                                        },
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        if (showYoloWarning) {
                            AlertDialog(
                                onDismissRequest = { showYoloWarning = false },
                                containerColor = colors.surface,
                                title = { Text(stringResource(R.string.settings_yolo_title), color = colors.accentRed, fontWeight = FontWeight.Bold) },
                                text = {
                                    Text(
                                        stringResource(R.string.settings_yolo_body) + " " +
                                        "No approval gate remains.",
                                        color = colors.textPrimary
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showYoloWarning = false
                                        viewModel.setAutoMode(AutoMode.YOLO)
                                    }) { Text(stringResource(R.string.settings_yolo_confirm), color = colors.accentRed) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showYoloWarning = false }) {
                                        Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        val grantedActions = config.effectiveGrantedActions()
                        Text(
                            text = "ALLOWED ACTIONS (${grantedActions.size})",
                            fontSize = 11.sp,
                            color = colors.accentCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val dateFormat = remember { java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()) }
                        grantedActions.entries
                            .sortedBy { it.key }
                            .forEach { (action, grantedAt) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        // CHECK_STOCK is the identifier the plan
                                        // uses; "Check stock" is the thing the
                                        // user is deciding whether to allow.
                                        Text(
                                            text = action.asActionLabel(),
                                            fontSize = 13.sp,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = if (grantedAt == 0L) "Default" else "Granted ${dateFormat.format(java.util.Date(grantedAt))}",
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                    TextButton(onClick = { viewModel.revokeGrant(action) }) {
                                        Text(stringResource(R.string.common_revoke), color = colors.accentRed, fontSize = 12.sp)
                                    }
                                }
                            }

                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.borderColor)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_multi_agent),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.settings_multi_agent_hint),
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                            Switch(
                                checked = config.multiAgentModeEnabled,
                                onCheckedChange = { viewModel.updateMultiAgentMode(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.accentNeonGreen,
                                    checkedTrackColor = colors.accentNeonGreen.copy(alpha = 0.5f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.borderColor)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_floating),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.settings_floating_hint),
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                            Switch(
                                checked = config.showFloatingButton,
                                onCheckedChange = { viewModel.updateShowFloatingButton(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.accentNeonGreen,
                                    checkedTrackColor = colors.accentNeonGreen.copy(alpha = 0.5f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.borderColor)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (config.isDarkMode) "Dark Mode" else "Light Mode",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.settings_theme_hint),
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                            Switch(
                                checked = config.isDarkMode,
                                onCheckedChange = { viewModel.updateDarkMode(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.accentNeonGreen,
                                    checkedTrackColor = colors.accentNeonGreen.copy(alpha = 0.5f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        AccentPicker()
                        }
                        }
                    }
                }
            }

            // Auto-Reply Settings Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAutoReply() },
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = stringResource(R.string.settings_auto_reply),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_auto_reply),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_auto_reply_hint),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.common_go),
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            // Notification History Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToNotificationHistory() },
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.settings_notif_history),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_notif_history),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_notif_history_hint),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.common_go),
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            // Permissions link card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPermissions() },
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = stringResource(R.string.settings_permissions),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_permissions),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_permissions_hint),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.common_go),
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            // The three screens that used to be tabs.
            item {
                SettingsLinkCard(
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    title = "Plan",
                    subtitle = "What the agent is doing now, and what it has run.",
                    onClick = onNavigateToPlan,
                )
            }
            item {
                SettingsLinkCard(
                    icon = Icons.Default.Build,
                    title = "Macros",
                    subtitle = "Saved sequences you can run again.",
                    onClick = onNavigateToMacros,
                )
            }
            item {
                SettingsLinkCard(
                    icon = Icons.Default.History,
                    title = "Logs",
                    subtitle = "Every executed step, and the actions that failed.",
                    onClick = onNavigateToLogs,
                )
            }

            // Habits & Routines link card
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
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = stringResource(R.string.settings_routines),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_routines),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_routines_hint),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.common_go),
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            // Crash Log link card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCrashLog() },
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = stringResource(R.string.settings_crash_log),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_crash_log),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_crash_log_hint),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.common_go),
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            // Privacy Policy link card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPrivacyPolicy() },
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.settings_privacy),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_privacy),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_privacy_hint),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.common_go),
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            // Terms of Use link card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTermsOfUse() },
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = stringResource(R.string.settings_terms),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_terms),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_terms_hint),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.common_go),
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            // Help Center link card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToHelpCenter() },
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = stringResource(R.string.settings_help),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_help),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_help_hint),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.common_go),
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            // License link card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToLicense() },
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = stringResource(R.string.settings_license),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_license),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_license_hint),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.common_go),
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            // About link card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAbout() },
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.settings_about),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_about_opendroid),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_about_hint),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.common_go),
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            // System integration info card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_integration_perms),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.settings_accessibility_hint),
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }

    val localImportStatus by viewModel.localImportStatus.collectAsState()

    if (showAuthRequiredDialog != null) {
        AlertDialog(
            onDismissRequest = { showAuthRequiredDialog = null },
            title = { Text(stringResource(R.string.settings_auth_required), color = colors.textPrimary) },
            text = {
                Text(
                    text = stringResource(R.string.settings_gated_model) + "\n\n" +
                        "Models tagged PUBLIC (for example Qwen 2.5 and the Gemma 4 community mirrors) do not need a token — only the ones tagged GATED do. " +
                        "Add a read-only token in the Hugging Face section above, or pick a PUBLIC model.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { showAuthRequiredDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange)
                ) {
                    Text(stringResource(R.string.common_ok), color = colors.background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAuthRequiredDialog = null }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            },
            containerColor = colors.cardBackground,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary
        )
    }

    if (showCellularWarningDialog != null) {
        val modelIdToDownload = showCellularWarningDialog!!
        AlertDialog(
            onDismissRequest = { showCellularWarningDialog = null },
            title = { Text(stringResource(R.string.settings_cellular_title), color = colors.textPrimary) },
            text = {
                Text(
                    text = stringResource(R.string.settings_cellular_body),
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCellularWarningDialog = null
                        viewModel.downloadModel(modelIdToDownload)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange)
                ) {
                    Text(stringResource(R.string.common_download), color = colors.background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCellularWarningDialog = null }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            },
            containerColor = colors.cardBackground,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary
        )
    }

    if (pendingCellularResumeModelId != null) {
        val modelIdToResume = pendingCellularResumeModelId!!
        AlertDialog(
            onDismissRequest = { pendingCellularResumeModelId = null },
            title = { Text(stringResource(R.string.settings_cellular_title), color = colors.textPrimary) },
            text = {
                Text(
                    text = stringResource(R.string.settings_cellular_body),
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingCellularResumeModelId = null
                        viewModel.resumeDownload(modelIdToResume)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange)
                ) {
                    Text(stringResource(R.string.common_resume), color = colors.background)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCellularResumeModelId = null }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            },
            containerColor = colors.cardBackground,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary
        )
    }

    if (localImportStatus != null) {
        val isImporting = localImportStatus == "Importing..."
        val isSuccess = localImportStatus == "Success"
        AlertDialog(
            onDismissRequest = {
                if (!isImporting) {
                    viewModel.clearImportStatus()
                }
            },
            title = {
                Text(
                    text = when {
                        isImporting -> "Importing Model"
                        isSuccess -> "Import Successful"
                        else -> "Import Failed"
                    },
                    color = colors.textPrimary
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    when {
                        isImporting -> {
                            CircularProgressIndicator(color = colors.accentOrange)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.settings_import_progress), color = colors.textSecondary)
                        }
                        isSuccess -> {
                            Text(stringResource(R.string.settings_import_done), color = colors.textSecondary)
                        }
                        else -> {
                            Text(
                                text = localImportStatus
                                    ?: "Failed to import model. Please make sure it is a valid LiteRT model file (.task or .litertlm) and is not corrupted.",
                                color = colors.accentRed
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!isImporting) {
                    Button(
                        onClick = { viewModel.clearImportStatus() },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange)
                    ) {
                        Text(stringResource(R.string.common_ok), color = colors.background)
                    }
                }
            },
            containerColor = colors.cardBackground,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary
        )
    }
}

private fun connectionStatusLabel(state: ConnectionTestState?): String = when (state) {
    is ConnectionTestState.Testing -> "Testing…"
    is ConnectionTestState.Connected ->
        "Connected with ${state.model} · ${state.latencyMs} ms"
    is ConnectionTestState.Failed -> when (state.error) {
        LLMError.AuthInvalid -> "Key rejected"
        LLMError.AuthMissing -> "Key required"
        LLMError.QuotaExhausted -> "Quota exhausted"
        LLMError.RateLimited -> "Rate limited"
        LLMError.Network -> "Network error"
        else -> "Connection failed"
    }
    is ConnectionTestState.ConfigMissing -> when (state.reason) {
        LLMError.AuthMissing -> "Key required"
        else -> "Configuration required"
    }
    else -> "Not tested"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        Locale.getDefault(),
        "%.1f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups]
    )
}

@Composable
private fun SecureApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalOpenDroidColors.current
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide API key" else "Show API key",
                    tint = colors.textSecondary
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accentNeonGreen,
            unfocusedBorderColor = colors.borderColor,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * `CHECK_STOCK` becomes `Check stock`.
 *
 * The grant list shows the identifier the planner uses, which is fine in a log
 * and wrong in a list of things a person is deciding whether to allow.
 */
private fun String.asActionLabel(): String =
    replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

/**
 * One row that leads somewhere else: icon, title, a line saying what is there,
 * and a chevron.
 *
 * The navigation rows in this screen were eight copies of the same twenty lines,
 * which is how five of them ended up wearing the same grey "i".
 */
@Composable
private fun SettingsLinkCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalOpenDroidColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary
            )
        }
    }
}

/**
 * The accent swatches.
 *
 * Circles rather than a dropdown: the thing being chosen is a colour, and a
 * colour named in a list is a word you have to imagine, while a colour shown is
 * the answer itself. The selected one wears a ring in its own colour rather than
 * a tick, so nothing on the row is drawn in a colour that is not one of them.
 */
@Composable
private fun AccentPicker() {
    val colors = LocalOpenDroidColors.current
    val store = rememberAccentStore()
    val selectedId by store.accentId.collectAsState()

    Column {
        Text(
            text = stringResource(R.string.settings_accent),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_accent_hint),
            fontSize = 12.sp,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ACCENT_OPTIONS.forEach { option ->
                val swatch = if (colors.isDark) option.dark else option.light
                val isSelected = option.id == selectedId
                val ring by animateDpAsState(
                    if (isSelected) 2.dp else 0.dp,
                    label = "accentRing",
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(ring, swatch, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .clickable { store.select(option.id) }
                        .semantics { contentDescription = option.label }
                )
            }
        }
    }
}

/**
 * Signing in to Codex, on this phone.
 *
 * There is no address and no token to type: the button opens the ChatGPT
 * consent page in a browser tab, the redirect comes back to a listener inside
 * this app, and the session that results is the credential. It is stored in the
 * Keystore, refreshed on its own, and can be dropped again from here.
 */
@Composable
private fun CodexSignIn(viewModel: SettingsViewModel) {
    val colors = LocalOpenDroidColors.current
    val auth by viewModel.codexAuth.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshCodexState() }

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.settings_chatgpt_account),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))

    val (status, statusColor) = when (val state = auth) {
        CodexAccountState.Unknown -> "Checking…" to colors.textSecondary
        CodexAccountState.Working -> "Finish the sign-in in your browser…" to colors.textSecondary
        CodexAccountState.SignedOut -> "Not signed in." to colors.accentOrange
        is CodexAccountState.SignedIn -> state.describe() to colors.accentNeonGreen
        is CodexAccountState.Failed -> state.message to colors.accentRed
    }
    Text(text = status, fontSize = 12.sp, lineHeight = 17.sp, color = statusColor)

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.settings_codex_hint),
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = colors.textSecondary,
    )

    Spacer(modifier = Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val signedIn = auth is CodexAccountState.SignedIn
        TextButton(
            onClick = { viewModel.startCodexSignIn() },
            enabled = auth != CodexAccountState.Working,
        ) {
            Text(
                text = if (signedIn) "Sign in again" else "Sign in with ChatGPT",
                fontSize = 12.sp,
                color = colors.accentNeonGreen
            )
        }
        if (signedIn) {
            TextButton(onClick = { viewModel.signOutOfCodex() }) {
                Text(stringResource(R.string.settings_sign_out), fontSize = 12.sp, color = colors.textSecondary)
            }
        }
    }
}

/**
 * The language the assistant answers in.
 *
 * Three segments rather than a dropdown: there are only three, and a choice
 * this consequential should be readable without opening anything.
 */
@Composable
private fun AppLanguageRow() {
    val colors = LocalOpenDroidColors.current
    val store = rememberAppLanguageStore()
    val selected by store.language.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_assistant_language), fontSize = 14.sp, color = colors.textPrimary)
        Text(
            text = stringResource(R.string.settings_assistant_language_hint),
            fontSize = 11.sp,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.background)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            AppLanguage.entries.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) colors.accentNeonGreen.copy(alpha = 0.16f) else Color.Transparent
                        )
                        .clickable { store.select(option) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = if (isSelected) colors.accentNeonGreen else colors.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Whether a typed question also gets a spoken answer.
 *
 * Off by default. Speech follows the question - spoken in, spoken out - so this
 * exists for people who want the phone to read everything, not as the way to
 * stop it talking over a typed conversation.
 */
@Composable
private fun SpeakTypedRepliesRow() {
    val colors = LocalOpenDroidColors.current
    val store = rememberSpeechOutputStore()
    val speakTyped by store.speakTypedReplies.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_speak_typed), fontSize = 14.sp, color = colors.textPrimary)
            Text(
                text = stringResource(R.string.settings_speak_typed_hint),
                fontSize = 11.sp,
                color = colors.textSecondary
            )
        }
        Switch(
            checked = speakTyped,
            onCheckedChange = { store.setSpeakTypedReplies(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.accentNeonGreen,
                checkedTrackColor = colors.accentNeonGreen.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * Choosing the Indonesian voice, by ear.
 *
 * Android exposes a voice's locale, quality and latency, but not its gender and
 * not a name anyone would recognise - so there is no honest way to label one
 * "male". Every installed voice is listed instead, and tapping one speaks a
 * sample immediately: the only way to tell them apart is to hear them.
 */
@Composable
private fun IndonesianVoicePicker() {
    val colors = LocalOpenDroidColors.current
    val context = LocalContext.current
    val store = rememberTtsVoiceStore()
    val selected by store.indonesianVoice.collectAsState()

    var voices by remember { mutableStateOf<List<String>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }

    val previewer = remember(context) { TtsVoicePreview(context) }
    DisposableEffect(previewer) {
        previewer.start(SpokenLanguage.INDONESIAN) { names -> voices = names }
        onDispose { previewer.release() }
    }

    val currentLabel = selected
        ?.let { name -> voices.indexOf(name).takeIf { it >= 0 }?.let { voiceDisplayLabel(name, it) } ?: name }
        ?: "Engine default"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = voices.isNotEmpty()) { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_indonesian_voice), fontSize = 14.sp, color = colors.textPrimary)
                Text(
                    text = if (voices.isEmpty()) {
                        "Reading the installed voices…"
                    } else {
                        "$currentLabel · ${voices.size} installed. Tap one to hear it."
                    },
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.settings_toggle_voice_list),
                tint = colors.textSecondary
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                VoiceChoiceRow(
                    label = stringResource(R.string.settings_voice_engine_default),
                    detail = "Whatever the system picks",
                    isSelected = selected == null,
                    onClick = { store.selectIndonesian(null) },
                )
                voices.forEachIndexed { index, name ->
                    VoiceChoiceRow(
                        label = voiceDisplayLabel(name, index),
                        detail = name,
                        isSelected = selected == name,
                        onClick = {
                            store.selectIndonesian(name)
                            previewer.preview(name, VOICE_SAMPLE)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceChoiceRow(
    label: String,
    detail: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalOpenDroidColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (isSelected) colors.accentNeonGreen else colors.textPrimary
            )
            Text(text = detail, fontSize = 10.sp, color = colors.textSecondary)
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.common_selected),
                tint = colors.accentNeonGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private const val VOICE_SAMPLE =
    "Halo, aku OpenDroid. Ada yang bisa kubantu hari ini?"

private fun CodexAccountState.SignedIn.describe(): String {
    val who = email.ifBlank { "Signed in" }
    val plan = planType.takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercase() }
        ?: return who
    return "$who · ChatGPT $plan"
}





