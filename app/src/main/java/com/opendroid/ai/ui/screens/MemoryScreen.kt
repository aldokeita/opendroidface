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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import com.opendroid.ai.R
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

/**
 * The five things the agent keeps, and what each is called on screen.
 *
 * [title] is the tile label. [blurb] is the line under the picker that explains
 * what you are looking at, because "episodic" and "procedural" are the words the
 * memory system uses, not words anyone else would reach for.
 */
enum class MemoryScreenTab(val title: String, val blurb: String) {
    GROWTH_GRAPH("Knowledge", "People, places and habits it has worked out about you."),
    SEMANTIC("Facts", "Things you told it to remember."),
    WORKING("Working", "What it is holding on to for the task in hand."),
    EPISODIC("Episodes", "Conversations it can look back on."),
    PROCEDURAL("Macros", "Sequences it has learned to repeat."),
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

    // What each store is holding, for the tiles. Collected here rather than
    // inside each view so the picker can show all five counts at once, which is
    // the whole reason it is tiles instead of tabs.
    val graph by viewModel.knowledgeGraph.collectAsState()
    val memories by viewModel.memoriesList.collectAsState()
    val conversations by viewModel.conversationHistory.collectAsState()
    val macros by viewModel.macrosList.collectAsState()
    val tabCounts: Map<MemoryScreenTab, Int?> = mapOf(
        MemoryScreenTab.GROWTH_GRAPH to graph.nodes.size,
        MemoryScreenTab.SEMANTIC to memories.count { it.type == MemoryType.SEMANTIC },
        // Working memory is device state - battery, network, where you are - not a
        // pile of items, so there is no number to put on the tile.
        MemoryScreenTab.WORKING to null,
        MemoryScreenTab.EPISODIC to conversations.size,
        MemoryScreenTab.PROCEDURAL to macros.size,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.memory_title),
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
                                contentDescription = stringResource(R.string.common_more),
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
            // Tiles, not tabs. Five scrollable tabs across the top said only what
            // each store is called, in the memory system's own vocabulary, and one
            // of them was always cut off. A tile carries its count, which is the
            // thing worth knowing at a glance - most of these are empty most of
            // the time, and a tab row cannot tell you that.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .bleedHorizontally(16.dp)
            ) {
                items(MemoryScreenTab.values()) { tab ->
                    MemoryStoreTile(
                        label = tab.title,
                        count = tabCounts[tab],
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            searchQuery = "" // Reset search query when changing tabs
                            isAddingFact = false
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // One line saying what the selected store actually holds. "Episodic"
            // and "procedural" are the memory system's words, not anyone else's.
            Text(
                text = selectedTab.blurb,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar & Add Button (Conditional)
            if (selectedTab != MemoryScreenTab.WORKING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Filled and borderless, the same control as the chat
                    // composer. An outlined field with a floating label was a
                    // second form language on a screen that only asks one thing.
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search ${selectedTab.title.lowercase()}",
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.memory_clear_search),
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = colors.cardBackground,
                            unfocusedContainerColor = colors.cardBackground,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accentNeonGreen,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    )
                    if (selectedTab == MemoryScreenTab.SEMANTIC) {
                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(
                            onClick = { isAddingFact = !isAddingFact },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(colors.accentGreenButton)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.memory_add), tint = colors.background)
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

/**
 * One store in the picker: how much is in it, and what it is called.
 *
 * The count is the larger of the two, because on this screen the question is
 * almost always "is there anything in there" rather than "what is it called".
 */
@Composable
private fun MemoryStoreTile(
    label: String,
    /** Null for a store that holds state rather than items; shown as a dash. */
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalOpenDroidColors.current
    val container by animateColorAsState(
        if (selected) colors.accentNeonGreen.copy(alpha = 0.14f) else colors.cardBackground,
        tween(220),
        label = "tileContainer",
    )
    val content by animateColorAsState(
        if (selected) colors.accentNeonGreen else colors.textSecondary,
        tween(220),
        label = "tileContent",
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = count?.toString() ?: "—",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (count == 0 && !selected) colors.textSecondary.copy(alpha = 0.5f) else content,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp,
            color = content,
        )
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
                        text = stringResource(R.string.memory_device_state),
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
                        text = stringResource(R.string.memory_active_plan),
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
                                text = stringResource(R.string.memory_empty_plan),
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
                        text = stringResource(R.string.memory_recent_sessions),
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
                                text = stringResource(R.string.memory_empty_session),
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
                text = stringResource(R.string.memory_empty_episodic),
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
                        text = stringResource(R.string.memory_new_fact),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = onNewKeyChange,
                        label = { Text(stringResource(R.string.memory_fact_key), fontSize = 12.sp) },
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
                        label = { Text(stringResource(R.string.memory_fact_value), fontSize = 12.sp) },
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
                            Text(stringResource(R.string.common_cancel), color = colors.accentRed)
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
                            Text(stringResource(R.string.memory_save_fact), fontWeight = FontWeight.Bold)
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
                    text = stringResource(R.string.memory_empty_facts),
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
                    contentDescription = stringResource(R.string.memory_delete),
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
                                text = stringResource(R.string.macros_trigger_prefix) + "\"${macro.trigger}\"",
                                fontSize = 12.sp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.memory_steps),
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
                                    contentDescription = stringResource(R.string.memory_delete_macro),
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
                text = stringResource(R.string.memory_empty_macros),
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
                    label = { Text(stringResource(R.string.memory_filter_all), fontSize = 10.sp) },
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
                            label = { Text(stringResource(R.string.memory_long_term), fontSize = 11.sp) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = addIsSensitive,
                            onClick = { addIsSensitive = true },
                            label = { Text(stringResource(R.string.memory_keystore), fontSize = 11.sp) }
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
                            Text(stringResource(R.string.common_cancel), color = colors.accentRed)
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
                            Text(stringResource(R.string.memory_save_entry), fontWeight = FontWeight.Bold)
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
                    text = stringResource(R.string.memory_empty_graph),
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
                            contentDescription = stringResource(R.string.memory_delete_node),
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
                        Text(stringResource(R.string.memory_promote), fontSize = 10.sp, color = colors.accentNeonGreen)
                    }
                }
            }
        }
    }
}



