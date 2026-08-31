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
                        text = "Settings",
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
                                text = "CREDENTIALS MUST BE RE-ENTERED",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentOrange
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Saved provider credentials cannot be read on this device. " +
                                    "Clear unavailable records, then enter your API keys again.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = viewModel::resetProviderCredentialsForReentry,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange)
                            ) {
                                Text("Clear unavailable credentials", color = colors.background)
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
                                text = "CREDENTIALS WERE NOT SAVED",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentOrange
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Secure credential storage is unavailable. Existing settings " +
                                    "were preserved; check device storage and try again.",
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
                            text = "Brain",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Who answers, and what it needs to.",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "PROVIDER",
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
                                    contentDescription = "Dropdown",
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
                                            text = "OFFLINE AI",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary
                                        ) 
                                    },
                                    enabled = false,
                                    onClick = {}
                                )
                                DropdownMenuItem(
                                    text = { Text("On-Device AI", color = colors.textPrimary, modifier = Modifier.padding(start = 8.dp)) },
                                    onClick = {
                                        viewModel.updateActiveProvider("On-Device AI")
                                        providerDropdownExpanded = false
                                    }
                                )
                                
                                Divider(color = colors.borderColor, thickness = 1.dp)

                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = "CLOUD AI",
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
                                text = "MODEL",
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
                                        contentDescription = "Refresh models",
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
                                    Text("Model name", color = colors.textSecondary, fontSize = 15.sp)
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
                                            contentDescription = "Show models dropdown",
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
                        val providerNeedsKey =
                            config.activeProvider != "Ollama" && config.activeProvider != "On-Device AI"
                        if (providerNeedsKey) {
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "API KEY",
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
                                        "Test connection",
                                        fontSize = 12.sp,
                                        color = colors.accentNeonGreen
                                    )
                                }
                            }
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
                                text = "PLANNING FALLBACKS",
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
                                    text = "Only selected providers may receive a retry after an unusable low-impact local plan. High-impact plans never switch automatically.",
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
                            contentDescription = "Benchmark",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Latency benchmark",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "View live charts comparing speeds & latency.",
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
                                text = "OLLAMA LOCAL ENDPOINT",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = config.ollamaUrl,
                                onValueChange = { viewModel.updateOllamaUrl(it) },
                                label = { Text("Ollama Server URL", fontSize = 12.sp) },
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
                                text = "Use local LAN IP (e.g. http://192.168.1.50:11434) if testing from a physical Android device.",
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
                                text = "ON-DEVICE AI STATUS",
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
                                text = "ANDROID AI CORE",
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
                                    Text("Download Gemma 4 (AI Core)", color = colors.background)
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
                                    Text("Download Gemma 3n (AI Core)", color = colors.background)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = colors.borderColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // ─── Hugging Face Section ───
                            Text(
                                text = "HUGGING FACE TOKEN (GATED MODELS ONLY)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentOrange
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Needed only for gated Hugging Face downloads (the Google-hosted Gemma 3n LiteRT builds). Public models such as Qwen 2.5 and the Gemma 4 community mirrors download without a token. Not used for cloud API providers.",
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
                                        label = { Text("Hugging Face Access Token", fontSize = 12.sp) },
                                        singleLine = true,
                                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                        placeholder = { Text("hf_...", fontSize = 12.sp, color = colors.textSecondary) },
                                        trailingIcon = {
                                            IconButton(onClick = { showToken = !showToken }) {
                                                Icon(
                                                    imageVector = if (showToken) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = "Toggle Token Visibility",
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
                                                Text("📋 Paste", fontSize = 11.sp, color = colors.accentOrange)
                                            }

                                            TextButton(
                                                onClick = { viewModel.updateHuggingFaceToken("") },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("❌ Clear", fontSize = 11.sp, color = colors.accentRed)
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
                                            Text("Storage: Encrypted", fontSize = 9.sp, color = colors.textSecondary)
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { viewModel.validateHuggingFaceToken() },
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentOrange),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("Validate Token", fontSize = 10.sp, color = colors.background, fontWeight = FontWeight.Bold)
                                            }

                                            if (hfToken.isNotBlank()) {
                                                Button(
                                                    onClick = { viewModel.removeHuggingFaceToken() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentRed.copy(alpha = 0.2f)),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("Remove Token", fontSize = 10.sp, color = colors.accentRed, fontWeight = FontWeight.Bold)
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
                                text = "LITERT-LM (FALLBACK)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentOrange
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Runs without Google AI Core. Models tagged PUBLIC (Qwen, the Gemma 4 community mirrors) need no HF token; models tagged GATED (the Google-hosted Gemma 3n builds) do. Or import your own .task / .litertlm file.",
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
                                                        Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(12.dp), tint = colors.textPrimary)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Pause", fontSize = 10.sp, color = colors.textPrimary)
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
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(12.dp), tint = colors.background)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Resume", fontSize = 10.sp, color = colors.background)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Button(
                                                    onClick = { viewModel.cancelDownload(spec.id) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentRed.copy(alpha = 0.6f)),
                                                    modifier = Modifier.height(28.dp).padding(horizontal = 4.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Cancel", fontSize = 10.sp, color = Color.White)
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
                                                    Text("Open Model Page", color = colors.accentOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                                                                Text("Download", fontSize = 11.sp, color = colors.background)
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
                                                            Text("Import", fontSize = 11.sp, color = colors.textPrimary)
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
                                                             Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = colors.accentRed)
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
                                                        Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(14.dp), tint = colors.textSecondary)
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
                                text = "CUSTOM LITERT MODELS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentOrange
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Import any .task or .litertlm file as its own model (not tied to a catalog slot). GGUF is not supported yet.",
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
                                Text("Import custom LiteRT model", fontSize = 12.sp, color = colors.background)
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
                                                            contentDescription = "Delete",
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
                                text = "STORAGE CLEANUP",
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
                                 Text("Delete Unused Models", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                text = "COPILOT LOCAL ENDPOINT",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = config.copilotUrl,
                                onValueChange = { viewModel.updateCopilotUrl(it) },
                                label = { Text("Copilot Server URL", fontSize = 12.sp) },
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
                                text = "Use local LAN IP (e.g. http://192.168.1.50:4141) if testing from a physical Android device.",
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
                                text = "CUSTOM OPENAI ENDPOINT",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = config.customEndpoints["Custom OpenAI Compatible"] ?: "",
                                onValueChange = { viewModel.updateCustomEndpoint("Custom OpenAI Compatible", it) },
                                label = { Text("Base URL (e.g. https://api.openai.com/v1)", fontSize = 12.sp) },
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
                                text = "Provide the custom OpenAI-compatible API base URL (e.g. from Pollination, Aqua Dev, Portkey, etc.)",
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
                                text = "KEYS FOR OTHER PROVIDERS",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Icon(
                                imageVector = if (keysSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Keys Section",
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
                                            Text("Test connection", fontSize = 11.sp)
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
                                text = "ELEVENLABS VOICE SYNTHESIS",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Icon(
                                imageVector = if (voiceSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Voice Section",
                                tint = colors.textSecondary
                            )
                        }

                        AnimatedVisibility(visible = voiceSectionExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SecureApiKeyField(
                                    value = config.elevenLabsApiKey,
                                    onValueChange = { viewModel.updateElevenLabsApiKey(it) },
                                    label = "ElevenLabs API Key"
                                )
                                OutlinedTextField(
                                    value = config.elevenLabsVoiceId,
                                    onValueChange = { viewModel.updateElevenLabsVoiceId(it) },
                                    label = { Text("ElevenLabs Voice ID", fontSize = 12.sp) },
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
                                    text = "If ElevenLabs key is not set, OpenDroid automatically falls back to native offline Android Text-to-Speech.",
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
                                text = "PLANNING & AUTOMATION",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Icon(
                                imageVector = if (planningSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Planning Section",
                                tint = colors.textSecondary
                            )
                        }

                        AnimatedVisibility(visible = planningSectionExpanded) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {

                        var showYoloWarning by remember { mutableStateOf(false) }
                        val autoMode = config.resolvedAutoMode()

                        Text(
                            text = "Auto Mode",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Auto runs plans whose every step you've allowed. YOLO runs everything without asking.",
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
                                title = { Text("Enable YOLO mode?", color = colors.accentRed, fontWeight = FontWeight.Bold) },
                                text = {
                                    Text(
                                        "YOLO runs EVERY plan without asking — including actions that " +
                                        "spend money (UPI payments, food and cab orders) and irreversible " +
                                        "ones (installing apps, deleting files, restarting the device). " +
                                        "No approval gate remains.",
                                        color = colors.textPrimary
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showYoloWarning = false
                                        viewModel.setAutoMode(AutoMode.YOLO)
                                    }) { Text("I understand, enable", color = colors.accentRed) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showYoloWarning = false }) {
                                        Text("Cancel", color = colors.textSecondary)
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
                                        Text("Revoke", color = colors.accentRed, fontSize = 12.sp)
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
                                    text = "Multi-Agent Planning Mode",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Use critic and plan merger agents for safer, more robust plan generation.",
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
                                    text = "Show Floating Button",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Show a tiny floating bubble to launch the app or record commands directly.",
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
                                    text = "Switch between dark and light appearance.",
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
                            contentDescription = "Auto-reply",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-reply",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Configure AI auto-reply for WhatsApp, SMS & Email.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Go",
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
                            contentDescription = "Notification history",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification history",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "View captured notifications and auto-reply log.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Go",
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
                            contentDescription = "Permissions",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permissions",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Review and grant microphone, storage, accessibility & other permissions.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Go",
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
                            contentDescription = "Habits and routines",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Habits & routines",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Detect repeated daily patterns & automate morning routines.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Go",
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
                            contentDescription = "Crash log",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Crash log",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "View and share crashes recorded on this device.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Go",
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
                            contentDescription = "Privacy Policy",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Privacy policy",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "How OpenDroid handles your data and privacy.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Go",
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
                            contentDescription = "Terms of Use",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Terms of use",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Usage terms and conditions for OpenDroid.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Go",
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
                            contentDescription = "Help Center",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Help center",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Guides, FAQs, and troubleshooting.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Go",
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
                            contentDescription = "License",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "License",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Open-source license and third-party credits.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Go",
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
                            contentDescription = "About",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "About OpenDroid",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Version info, features, and technology stack.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Go",
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
                            text = "SYSTEM INTEGRATION PERMISSIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "To allow OpenDroid to operate other applications autonomously (e.g. WhatsApp, Calendar), verify that the accessibility service 'OpenDroid' is active in Settings -> Accessibility -> Installed Services.",
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
            title = { Text("Authentication Required", color = colors.textPrimary) },
            text = {
                Text(
                    text = "This model is gated on Hugging Face and needs an Access Token to download.\n\n" +
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
                    Text("OK", color = colors.background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAuthRequiredDialog = null }) {
                    Text("Cancel", color = colors.textSecondary)
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
            title = { Text("Cellular Network Warning", color = colors.textPrimary) },
            text = {
                Text(
                    text = "You are downloading model on cellular network, data charges may apply.",
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
                    Text("Download", color = colors.background)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCellularWarningDialog = null }) {
                    Text("Cancel", color = colors.textSecondary)
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
            title = { Text("Cellular Network Warning", color = colors.textPrimary) },
            text = {
                Text(
                    text = "You are downloading model on cellular network, data charges may apply.",
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
                    Text("Resume", color = colors.background)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCellularResumeModelId = null }) {
                    Text("Cancel", color = colors.textSecondary)
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
                            Text("Copying and verifying the model file. This may take a minute...", color = colors.textSecondary)
                        }
                        isSuccess -> {
                            Text("The model was imported and verified successfully. You can now load it.", color = colors.textSecondary)
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
                        Text("OK", color = colors.background)
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
            text = "ACCENT",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Used across every screen, the navigation bar and its glow.",
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
