package com.opendroid.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.opendroid.ai.core.memory.graph.KnowledgeCategory
import com.opendroid.ai.core.memory.graph.KnowledgeNode
import com.opendroid.ai.core.memory.graph.MemoryTier
import com.opendroid.ai.data.models.ChatMessage
import com.opendroid.ai.data.models.Macro
import com.opendroid.ai.data.models.Memory
import com.opendroid.ai.data.models.MemoryType
import com.opendroid.ai.ui.theme.*
import com.opendroid.ai.ui.viewmodel.MemoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MemoryScreenTab(val title: String) {
    GROWTH_GRAPH("GROWTH GRAPH"),
    SEMANTIC("LONG-TERM"),
    WORKING("TEMPORARY"),
    EPISODIC("EPISODIC"),
    PROCEDURAL("MACROS")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalOpenDroidColors.current
    var selectedTab by remember { mutableStateOf(MemoryScreenTab.GROWTH_GRAPH) }
    var searchQuery by remember { mutableStateOf("") }
    var isAddingFact by remember { mutableStateOf(false) }
    
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var showMemoryMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Memory",
                        fontFamily = Montserrat,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 19.sp,
                        letterSpacing = (-0.3).sp
                    )
                },
                actions = {
                    // Wiping a whole memory category is destructive and rare. It
                    // used to sit in the top bar as a red word beside the title,
                    // which gave the most dangerous control on the screen the most
                    // prominent position on it.
                    Box {
                        IconButton(onClick = { showMemoryMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = colors.textSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded = showMemoryMenu,
                            onDismissRequest = { showMemoryMenu = false },
                            modifier = Modifier.background(colors.surface)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Wipe ${selectedTab.title.lowercase()}",
                                        color = colors.accentRed,
                                        fontSize = 13.sp,
                                    )
                                },
                                onClick = {
                                    showMemoryMenu = false
                                    when (selectedTab) {
                                        MemoryScreenTab.GROWTH_GRAPH -> viewModel.clearMemoryTier(MemoryTier.LEARNED_PATTERN)
                                        MemoryScreenTab.SEMANTIC -> viewModel.clearMemories(MemoryType.SEMANTIC)
                                        MemoryScreenTab.WORKING -> viewModel.clearMemories(MemoryType.WORKING)
                                        MemoryScreenTab.EPISODIC -> viewModel.clearMemories(MemoryType.EPISODIC)
                                        MemoryScreenTab.PROCEDURAL -> viewModel.clearMemories(MemoryType.PROCEDURAL)
                                    }
                                }
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
                .padding(horizontal = 16.dp)
        ) {
            // Memory Category Tabs. Bled out to the screen edge so the tab that
            // continues off-screen is visibly cut by the display rather than
            // ending mid-word inside a margin, which reads as a layout bug.
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = colors.background,
                contentColor = colors.accentNeonGreen,
                edgePadding = 16.dp,
                divider = { Divider(color = colors.borderColor) },
                modifier = Modifier.bleedHorizontally(16.dp)
            ) {
                MemoryScreenTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { 
                            selectedTab = tab
                            searchQuery = "" // Reset search query when changing tabs
                            isAddingFact = false
                        },
                        text = {
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar & Add Button (Conditional)
            if (selectedTab != MemoryScreenTab.WORKING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            val hint = when (selectedTab) {
                                MemoryScreenTab.GROWTH_GRAPH -> "Search Knowledge Graph..."
                                MemoryScreenTab.EPISODIC -> "Search conversation logs..."
                                MemoryScreenTab.PROCEDURAL -> "Search macros..."
                                else -> "Search facts..."
                            }
                            Text(hint, color = colors.textSecondary, fontSize = 13.sp)
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = colors.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentNeonGreen,
                            unfocusedBorderColor = colors.borderColor,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedTab == MemoryScreenTab.SEMANTIC) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { isAddingFact = !isAddingFact },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accentNeonGreen)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Memory", tint = colors.background)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Render Dynamic Tab Contents
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    MemoryScreenTab.GROWTH_GRAPH -> {
                        KnowledgeGraphView(viewModel = viewModel, searchQuery = searchQuery)
                    }
                    MemoryScreenTab.WORKING -> {
                        WorkingMemoryView(viewModel = viewModel)
                    }
                    MemoryScreenTab.EPISODIC -> {
                        EpisodicMemoryView(viewModel = viewModel, searchQuery = searchQuery)
                    }
                    MemoryScreenTab.SEMANTIC -> {
                        SemanticMemoryView(
                            viewModel = viewModel,
                            searchQuery = searchQuery,
                            isAddingFact = isAddingFact,
                            onIsAddingFactChange = { isAddingFact = it },
                            newKey = newKey,
                            onNewKeyChange = { newKey = it },
                            newValue = newValue,
                            onNewValueChange = { newValue = it }
                        )
                    }
                    MemoryScreenTab.PROCEDURAL -> {
                        ProceduralMemoryView(viewModel = viewModel, searchQuery = searchQuery)
                    }
                }
            }
        }
    }
}

/** `TASK_ROUTINE` becomes `Task routine`. */
private fun KnowledgeCategory.displayLabel(): String =
    name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

/**
 * Lets a horizontally scrolling row reach past its parent's side padding.
 *
 * A scrollable row inside a padded column stops at the margin, so the item that
 * continues off-screen ends in the middle of nowhere and reads as clipped text
 * rather than as "there is more this way". Running it to the display edge, with
 * its own content padding to keep the first item aligned with everything else,
 * is what makes the cut legible as scrolling. Compose has no negative padding,
 * hence the layout modifier.
 */
private fun Modifier.bleedHorizontally(amount: Dp) = this.layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        )
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-amount.roundToPx(), 0)
    }
}

@Composable
fun WorkingMemoryView(viewModel: MemoryViewModel) {
    val colors = LocalOpenDroidColors.current
    val activePlan by viewModel.activePlan.collectAsState()
    val workingMemory = viewModel.workingMemory
    
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Device State Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DEVICE STATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StateItem("Battery Level", "${workingMemory.batteryLevel}%", colors.accentNeonGreen)
                        StateItem("WiFi State", workingMemory.wifiState, if (workingMemory.wifiState == "Active") colors.accentNeonGreen else if (workingMemory.wifiState == "Inactive") colors.accentRed else colors.textSecondary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StateItem("Connectivity", workingMemory.connectivity, colors.accentCyan)
                        StateItem("Internet", if (workingMemory.isInternetAvailable) "Available" else "NOT AVAILABLE", if (workingMemory.isInternetAvailable) colors.accentNeonGreen else colors.accentRed)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StateItem("Location Context", workingMemory.locationContext, colors.textSecondary)
                    }
                }
            }
        }

        // 2. Active Plan Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ACTIVE PLAN",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val plan = activePlan
                    if (plan != null) {
                        Text(
                            text = plan.goal,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = when (plan.status.name) {
                                    "RUNNING" -> colors.accentCyan.copy(alpha = 0.2f)
                                    "COMPLETED" -> colors.accentNeonGreen.copy(alpha = 0.2f)
                                    else -> colors.accentRed.copy(alpha = 0.2f)
                                },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = plan.status.name,
                                    color = when (plan.status.name) {
                                        "RUNNING" -> colors.accentCyan
                                        "COMPLETED" -> colors.accentNeonGreen
                                        else -> colors.accentRed
                                    },
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = colors.borderColor)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        plan.steps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = when (step.status.name) {
                                        "COMPLETED" -> "●"
                                        "RUNNING" -> "▶"
                                        "FAILED" -> "✖"
                                        else -> "○"
                                    },
                                    color = when (step.status.name) {
                                        "COMPLETED" -> colors.accentNeonGreen
                                        "RUNNING" -> colors.accentCyan
                                        "FAILED" -> colors.accentRed
                                        else -> colors.textSecondary
                                    },
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${step.description}",
                                        fontSize = 12.sp,
                                        color = if (step.status.name == "COMPLETED") colors.textSecondary else colors.textPrimary,
                                        fontWeight = if (step.status.name == "RUNNING") FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (!step.result.isNullOrBlank()) {
                                        Text(
                                            text = "Result: ${step.result}",
                                            fontSize = 10.sp,
                                            color = colors.accentCyan,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    if (!step.error.isNullOrBlank()) {
                                        Text(
                                            text = "Error: ${step.error}",
                                            fontSize = 10.sp,
                                            color = colors.accentRed,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No active autonomous plan running.",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Current Session history
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RECENT SESSIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val history = workingMemory.conversationHistory
                    if (history.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            history.forEach { msg ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (msg.sender.name == "USER") Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        color = if (msg.sender.name == "USER") colors.accentCyan.copy(alpha = 0.15f) else colors.accentNeonGreen.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = if (msg.sender.name == "USER") "USER" else "AGENT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (msg.sender.name == "USER") colors.accentCyan else colors.accentNeonGreen
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = msg.text,
                                                fontSize = 12.sp,
                                                color = colors.textPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No messages in current working session.",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StateItem(label: String, value: String, valueColor: Color) {
    val colors = LocalOpenDroidColors.current
    Column {
        Text(text = label, color = colors.textSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EpisodicMemoryView(viewModel: MemoryViewModel, searchQuery: String) {
    val colors = LocalOpenDroidColors.current
    val conversations by viewModel.conversationHistory.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    val filteredLogs = conversations.filter {
        it.text.contains(searchQuery, ignoreCase = true) ||
        (it.modelBadge?.contains(searchQuery, ignoreCase = true) ?: false)
    }

    if (filteredLogs.isNotEmpty()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredLogs) { log ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (log.sender.name == "USER") "USER" else "AGENT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (log.sender.name == "USER") colors.accentCyan else colors.accentNeonGreen
                                )
                                log.modelBadge?.let { badge ->
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = colors.accentCyan.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = badge,
                                            fontSize = 9.sp,
                                            color = colors.accentCyan,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = dateFormat.format(Date(log.timestamp)),
                                fontSize = 9.sp,
                                color = colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = log.text,
                            fontSize = 13.sp,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No episodic chat logs recorded.",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun SemanticMemoryView(
    viewModel: MemoryViewModel,
    searchQuery: String,
    isAddingFact: Boolean,
    onIsAddingFactChange: (Boolean) -> Unit,
    newKey: String,
    onNewKeyChange: (String) -> Unit,
    newValue: String,
    onNewValueChange: (String) -> Unit
) {
    val colors = LocalOpenDroidColors.current
    val allMemories by viewModel.memoriesList.collectAsState()
    
    val filteredMemories = allMemories.filter {
        it.type == MemoryType.SEMANTIC && (
            it.key.contains(searchQuery, ignoreCase = true) ||
            it.value.contains(searchQuery, ignoreCase = true)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = isAddingFact) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "New fact",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = onNewKeyChange,
                        label = { Text("Fact Key/Identifier", fontSize = 12.sp) },
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
                        value = newValue,
                        onValueChange = onNewValueChange,
                        label = { Text("Fact Content/Details", fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentNeonGreen,
                            unfocusedBorderColor = colors.borderColor,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onIsAddingFactChange(false) }) {
                            Text("Cancel", color = colors.accentRed)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newKey.isNotBlank() && newValue.isNotBlank()) {
                                    viewModel.storeFact(newKey, newValue, MemoryType.SEMANTIC)
                                    onNewKeyChange("")
                                    onNewValueChange("")
                                    onIsAddingFactChange(false)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentNeonGreen, contentColor = colors.background),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Fact", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredMemories.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredMemories) { mem ->
                    MemoryItemCard(
                        memory = mem,
                        onDelete = { viewModel.deleteMemory(mem.key) }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No semantic facts indexed in this category.",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun MemoryItemCard(
    memory: Memory,
    onDelete: () -> Unit
) {
    val colors = LocalOpenDroidColors.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = memory.key,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentNeonGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = memory.value,
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Indexed: ${dateFormat.format(Date(memory.timestamp))}",
                    fontSize = 9.sp,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Memory",
                    tint = colors.textSecondary.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun ProceduralMemoryView(viewModel: MemoryViewModel, searchQuery: String) {
    val colors = LocalOpenDroidColors.current
    val macros by viewModel.macrosList.collectAsState()

    val filteredMacros = macros.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.trigger.contains(searchQuery, ignoreCase = true)
    }

    if (filteredMacros.isNotEmpty()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredMacros) { macro ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = macro.name.uppercase(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accentNeonGreen
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (macro.isSystem) colors.accentCyan.copy(alpha = 0.15f) else colors.textSecondary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (macro.isSystem) "SYSTEM" else "USER",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (macro.isSystem) colors.accentCyan else colors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Trigger: \"${macro.trigger}\"",
                                fontSize = 12.sp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "STEPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            macro.steps.forEachIndexed { index, step ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "  → ",
                                        fontSize = 11.sp,
                                        color = colors.accentCyan
                                    )
                                    Text(
                                        text = step.description,
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                        
                        if (!macro.isSystem) {
                            IconButton(onClick = { viewModel.deleteMacro(macro.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Macro",
                                    tint = colors.accentRed.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No custom macros or procedures registered.",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun KnowledgeGraphView(
    viewModel: MemoryViewModel,
    searchQuery: String
) {
    val colors = LocalOpenDroidColors.current
    val graph by viewModel.knowledgeGraph.collectAsState()
    var selectedTierFilter by remember { mutableStateOf<MemoryTier?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf<KnowledgeCategory?>(null) }
    var isAddingKnowledge by remember { mutableStateOf(false) }
    var addIsSensitive by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    var newSummary by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf(KnowledgeCategory.USER_PREFERENCE) }

    val allNodes = graph.nodes.values.toList()
    val filteredNodes = allNodes.filter { node ->
        (selectedTierFilter == null || node.tier == selectedTierFilter) &&
        (selectedCategoryFilter == null || node.category == selectedCategoryFilter) &&
        (searchQuery.isBlank() ||
            node.label.contains(searchQuery, ignoreCase = true) ||
            node.summary.contains(searchQuery, ignoreCase = true) ||
            node.properties.values.any { it.contains(searchQuery, ignoreCase = true) }
        )
    }.sortedWith(
        compareBy<KnowledgeNode> {
            when (it.tier) {
                MemoryTier.LONG_TERM -> 0
                MemoryTier.LEARNED_PATTERN -> 1
                MemoryTier.SENSITIVE -> 2
                MemoryTier.TEMPORARY -> 3
            }
        }.thenByDescending { it.confidence }
         .thenByDescending { it.lastUpdated }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Tier Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .bleedHorizontally(16.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedTierFilter == null,
                    onClick = { selectedTierFilter = null },
                    label = { Text("All (${allNodes.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentNeonGreen,
                        selectedLabelColor = colors.background,
                        containerColor = colors.cardBackground,
                        labelColor = colors.textSecondary
                    )
                )
            }
            items(MemoryTier.values()) { tier ->
                val count = allNodes.count { it.tier == tier }
                // The emoji and the "Level N:" prefix are gone: the tiers are
                // already ordered left to right, and the numbering pushed every
                // chip past the width the row had to give it.
                val label = when (tier) {
                    MemoryTier.TEMPORARY -> "Temporary"
                    MemoryTier.LONG_TERM -> "Long-term"
                    MemoryTier.LEARNED_PATTERN -> "Patterns"
                    MemoryTier.SENSITIVE -> "Sensitive"
                }
                FilterChip(
                    selected = selectedTierFilter == tier,
                    onClick = { selectedTierFilter = if (selectedTierFilter == tier) null else tier },
                    label = { Text("$label ($count)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (tier) {
                            MemoryTier.SENSITIVE -> colors.accentOrange
                            MemoryTier.LEARNED_PATTERN -> colors.accentCyan
                            else -> colors.accentNeonGreen
                        },
                        selectedLabelColor = colors.background,
                        containerColor = colors.cardBackground,
                        labelColor = colors.textSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Category Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .bleedHorizontally(16.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategoryFilter == null,
                    onClick = { selectedCategoryFilter = null },
                    label = { Text("All", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.textPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = colors.textPrimary,
                        containerColor = colors.cardBackground.copy(alpha = 0.6f),
                        labelColor = colors.textSecondary
                    )
                )
            }
            items(KnowledgeCategory.values()) { cat ->
                FilterChip(
                    selected = selectedCategoryFilter == cat,
                    onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat },
                    // Title case, not the raw enum. TASK_ROUTINE is what the code
                    // calls it; "Task routine" is what a person calls it.
                    label = { Text(cat.displayLabel(), fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentCyan.copy(alpha = 0.3f),
                        selectedLabelColor = colors.accentCyan,
                        containerColor = colors.cardBackground.copy(alpha = 0.6f),
                        labelColor = colors.textSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Add Knowledge Header Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ENTITIES (${filteredNodes.size})",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
            TextButton(onClick = { isAddingKnowledge = !isAddingKnowledge }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = colors.accentNeonGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isAddingKnowledge) "Close" else "Add Entity / Secret", color = colors.accentNeonGreen, fontSize = 11.sp)
            }
        }

        // Add Knowledge / Secret Card
        AnimatedVisibility(visible = isAddingKnowledge) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (addIsSensitive) "ADD LEVEL 4 ENCRYPTED SECRET" else "ADD LEVEL 2 LONG-TERM KNOWLEDGE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (addIsSensitive) colors.accentOrange else colors.accentNeonGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = !addIsSensitive,
                            onClick = { addIsSensitive = false },
                            label = { Text("🧠 Long-Term Memory", fontSize = 11.sp) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = addIsSensitive,
                            onClick = { addIsSensitive = true },
                            label = { Text("🔒 Keystore Encrypted", fontSize = 11.sp) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text(if (addIsSensitive) "Secret Key / Label (e.g. locker_code)" else "Label / Title (e.g. Favorite Coffee)", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (addIsSensitive) colors.accentOrange else colors.accentNeonGreen,
                            unfocusedBorderColor = colors.borderColor,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newSummary,
                        onValueChange = { newSummary = it },
                        label = { Text(if (addIsSensitive) "Secret Value (Hardware Encrypted)" else "Details / Description", fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (addIsSensitive) colors.accentOrange else colors.accentNeonGreen,
                            unfocusedBorderColor = colors.borderColor,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { isAddingKnowledge = false }) {
                            Text("Cancel", color = colors.accentRed)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newLabel.isNotBlank() && newSummary.isNotBlank()) {
                                    if (addIsSensitive) {
                                        viewModel.recordSensitiveData(newLabel, newSummary, newLabel)
                                    } else {
                                        viewModel.recordExplicitKnowledge(newLabel, newSummary, newCategory)
                                    }
                                    newLabel = ""
                                    newSummary = ""
                                    isAddingKnowledge = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (addIsSensitive) colors.accentOrange else colors.accentNeonGreen,
                                contentColor = colors.background
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Entry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredNodes.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredNodes, key = { it.id }) { node ->
                    KnowledgeNodeCard(
                        node = node,
                        onPromote = { viewModel.promotePattern(node.id) },
                        onDelete = { viewModel.deleteKnowledgeNode(node.id) }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No Knowledge Graph entities matching filter.",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun KnowledgeNodeCard(
    node: KnowledgeNode,
    onPromote: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalOpenDroidColors.current
    val tierColor = when (node.tier) {
        MemoryTier.SENSITIVE -> colors.accentOrange
        MemoryTier.LEARNED_PATTERN -> colors.accentCyan
        MemoryTier.TEMPORARY -> colors.textSecondary
        MemoryTier.LONG_TERM -> colors.accentNeonGreen
    }
    val tierIcon = when (node.tier) {
        MemoryTier.SENSITIVE -> "🔒"
        MemoryTier.LEARNED_PATTERN -> "📈"
        MemoryTier.TEMPORARY -> "⚡"
        MemoryTier.LONG_TERM -> "🧠"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = tierColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "$tierIcon ${node.tier.name}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = tierColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = colors.textPrimary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = node.category.name.replace('_', ' '),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (node.tier == MemoryTier.LEARNED_PATTERN) {
                        Text(
                            text = "${(node.confidence * 100).toInt()}% conf",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentCyan
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Node",
                            tint = colors.textSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = node.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentNeonGreen
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = node.summary,
                fontSize = 13.sp,
                color = colors.textPrimary
            )

            if (node.properties.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    node.properties.entries.take(3).forEach { (k, v) ->
                        Text(
                            text = "$k: $v",
                            fontSize = 9.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            if (node.tier == MemoryTier.LEARNED_PATTERN) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onPromote,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, colors.accentNeonGreen.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = colors.accentNeonGreen, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Promote to Long-Term", fontSize = 10.sp, color = colors.accentNeonGreen)
                    }
                }
            }
        }
    }
}
