package com.opendroid.ai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import com.opendroid.ai.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.BuildConfig
import com.opendroid.ai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_about),
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 19.sp,
                        letterSpacing = (-0.3).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = AppTheme.colors.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.background)
            )
        },
        containerColor = AppTheme.colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Identity Card
            item {
                // No gradient outline. A green-to-cyan border round the app's own
                // name was the loudest thing on a page of quiet grey text, and the
                // card already separates itself from the background by its fill.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.background),
                            contentAlignment = Alignment.Center
                        ) {
                            // bot.png is a 512x341 canvas whose glyph only covers the
                            // middle ~160x172, so it draws tiny at the badge's own size.
                            // requiredSize scales past the 80.dp parent constraint; the
                            // transparent padding overflows into the Box's circle clip.
                            Image(
                                painter = painterResource(id = R.drawable.bot),
                                contentDescription = stringResource(R.string.about_icon),
                                modifier = Modifier.requiredSize(150.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "OpenDroid",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AppTheme.colors.textPrimary,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.about_tagline),
                            fontSize = 14.sp,
                            color = AppTheme.colors.accentCyan,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME}",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                }
            }

            // Description Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppTheme.colors.borderColor, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about_what),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.about_body) + "\n\n" +
                                    "Powered by your choice of LLM provider (Gemini, OpenAI, Claude, Groq, local Ollama, and more), " +
                                    "OpenDroid combines intelligent planning with real device automation through Android's Accessibility framework.",
                            fontSize = 13.sp,
                            color = AppTheme.colors.textPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Features Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppTheme.colors.borderColor, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about_capabilities),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        FeatureItem(Icons.Default.Chat, "Natural Language Control", "Speak or type commands in plain English")
                        FeatureItem(Icons.Default.List, "Multi-Step Planning", "Automatically breaks complex tasks into executable steps")
                        FeatureItem(Icons.Default.Star, "Persistent Memory", "Remembers your preferences across sessions")
                        FeatureItem(Icons.Default.Build, "Custom Macros", "Record and replay complex workflows")
                        FeatureItem(Icons.Default.Accessibility, "App Automation", "Controls other apps via Accessibility Service")
                        FeatureItem(Icons.Default.Settings, "System Control", "WiFi, Bluetooth, flashlight, volume, and more")
                        FeatureItem(Icons.Default.Call, "Communication", "WhatsApp, calls, SMS, email — hands-free")
                        FeatureItem(Icons.Default.Lock, "Privacy-First", "All data stays on your device")
                    }
                }
            }

            // Tech Stack Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppTheme.colors.borderColor, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about_stack),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        TechItem("Language", "Kotlin")
                        TechItem("UI Framework", "Jetpack Compose + Material 3")
                        TechItem("Architecture", "MVVM + Hilt DI")
                        TechItem("Database", "Room (SQLite)")
                        TechItem("AI Integration", "Multi-provider LLM support")
                        TechItem("Automation", "Android Accessibility Service")
                        TechItem("Async", "Kotlin Coroutines + Flow")
                        TechItem("Serialization", "kotlinx.serialization")
                    }
                }
            }

            // Supported Providers Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppTheme.colors.borderColor, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about_providers),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val providers = listOf(
                            "Google Gemini", "OpenAI (GPT-4o, etc.)", "Anthropic Claude",
                            "Groq", "Mistral AI", "OpenRouter", "Together AI",
                            "Cohere", "DeepSeek", "Copilot API", "Ollama (Local)"
                        )
                        providers.forEach { provider ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AppTheme.colors.accentNeonGreen)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = provider,
                                    fontSize = 13.sp,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Open Source Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppTheme.colors.accentPurple.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.about_open_source),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.about_open_source_body),
                            fontSize = 13.sp,
                            color = AppTheme.colors.textPrimary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "github.com/yashab-cyber/opendroid",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.accentCyan
                        )
                    }
                }
            }

            // Footer
            item {
                Text(
                    text = stringResource(R.string.about_made_with),
                    fontSize = 12.sp,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureItem(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = AppTheme.colors.accentNeonGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = AppTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun TechItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = AppTheme.colors.textSecondary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.colors.textPrimary
        )
    }
}



