/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  presentation/screens/home/HomeScreen.kt ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.presentation.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mnmyounus.yacr.R
import com.mnmyounus.yacr.domain.model.CallType
import com.mnmyounus.yacr.domain.model.Recording
import com.mnmyounus.yacr.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            viewModel.onSnackbarDismissed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedContent(targetState = uiState.isSelectionMode) { selectionMode ->
                if (selectionMode) {
                    SelectionTopBar(
                        selectedCount = uiState.selectedIds.size,
                        totalCount    = uiState.recordings.size,
                        onClear       = viewModel::onClearSelection,
                        onSelectAll   = viewModel::onSelectAll,
                        onDelete      = viewModel::onDeleteSelected
                    )
                } else {
                    DefaultTopBar(onNavigateToSettings = onNavigateToSettings)
                }
            }
        },
        containerColor = YacrBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Search Bar ────────────────────────────────────────────────
            SearchBar(
                query    = uiState.searchQuery,
                onQuery  = viewModel::onSearchQueryChange,
                onClear  = viewModel::onSearchClear,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // ── Content ────────────────────────────────────────────────────
            when {
                uiState.isLoading -> LoadingState()
                !uiState.hasRecordings -> EmptyState(isSearchActive = uiState.isSearchActive)
                else -> RecordingsList(
                    recordings   = uiState.recordings,
                    selectedIds  = uiState.selectedIds,
                    selectionMode = uiState.isSelectionMode,
                    onTap         = { recording ->
                        if (uiState.isSelectionMode) viewModel.onRecordingSelect(recording.id)
                        else onNavigateToPlayer(recording.id)
                    },
                    onLongPress  = { viewModel.onRecordingLongPress(it.id) },
                    onDelete     = { viewModel.onDeleteSingle(it.id) },
                    onToggleFlag = { viewModel.onToggleFlag(it.id) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar Variants
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultTopBar(onNavigateToSettings: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordingIndicatorDot()
                Spacer(Modifier.width(10.dp))
                Text("YACR", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = YacrOnBackground)
            }
        },
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = YacrOnSurfaceVariant)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = YacrBackground)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = YacrOnBackground)
            }
        },
        title = {
            Text(
                "$selectedCount / $totalCount selected",
                fontWeight = FontWeight.Medium,
                color = YacrOnBackground
            )
        },
        actions = {
            if (selectedCount < totalCount) {
                TextButton(onClick = onSelectAll) {
                    Text("All", color = YacrPrimary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = YacrPrimary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = YacrSurface)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Search Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier,
        placeholder = { Text("Search by name or number…", color = YacrOnSurfaceVariant) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = YacrOnSurfaceVariant) },
        trailingIcon = if (query.isNotBlank()) {
            { IconButton(onClick = onClear) { Icon(Icons.Default.Clear, contentDescription = "Clear", tint = YacrOnSurfaceVariant) } }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = YacrPrimary,
            unfocusedBorderColor = YacrOutline,
            focusedTextColor     = YacrOnBackground,
            unfocusedTextColor   = YacrOnBackground,
            cursorColor          = YacrPrimary,
            focusedContainerColor   = YacrSurfaceVariant,
            unfocusedContainerColor = YacrSurfaceVariant
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Recordings List
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingsList(
    recordings: List<Recording>,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    onTap: (Recording) -> Unit,
    onLongPress: (Recording) -> Unit,
    onDelete: (Recording) -> Unit,
    onToggleFlag: (Recording) -> Unit
) {
    LazyColumn(
        contentPadding    = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(recordings, key = { it.id }) { recording ->
            RecordingCard(
                recording    = recording,
                isSelected   = recording.id in selectedIds,
                selectionMode = selectionMode,
                onTap        = { onTap(recording) },
                onLongPress  = { onLongPress(recording) },
                onDelete     = { onDelete(recording) },
                onToggleFlag = { onToggleFlag(recording) },
                modifier     = Modifier.animateItemPlacement()
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recording Card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingCard(
    recording: Recording,
    isSelected: Boolean,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onToggleFlag: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        if (isSelected) YacrPrimary else Color.Transparent,
        label = "border"
    )

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick      = onTap,
                onLongClick  = onLongPress
            ),
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2A1010) else YacrCardSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Avatar / Checkbox ──────────────────────────────────────
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onTap() },
                    colors = CheckboxDefaults.colors(
                        checkedColor   = YacrPrimary,
                        uncheckedColor = YacrOnSurfaceVariant
                    )
                )
            } else {
                CallTypeAvatar(callType = recording.callType)
            }

            Spacer(Modifier.width(12.dp))

            // ── Call Info ──────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = recording.callerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = YacrOnBackground,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (recording.isFlagged) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Flagged",
                            tint = YacrAccentAmber,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = formatTimestamp(recording.startTimestampMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = YacrOnSurfaceVariant
                    )
                    Text("·", color = YacrOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text  = formatDuration(recording.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = YacrOnSurfaceVariant
                    )
                    Text("·", color = YacrOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text  = formatSize(recording.fileSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = YacrOnSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                EncryptedBadge()
            }

            Spacer(Modifier.width(8.dp))

            // ── Actions ────────────────────────────────────────────────
            if (!selectionMode) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onToggleFlag, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (recording.isFlagged) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Flag",
                            tint = if (recording.isFlagged) YacrAccentAmber else YacrOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = YacrOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete recording?", color = YacrOnBackground) },
            text = {
                Text(
                    "Permanently delete the encrypted recording of ${recording.callerName}? " +
                    "This cannot be undone.",
                    color = YacrOnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = YacrPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = YacrOnSurfaceVariant)
                }
            },
            containerColor = YacrSurface
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CallTypeAvatar(callType: CallType) {
    val bgColor = when {
        callType.isCellular -> YacrSecondary
        else -> Color(0xFF1A237E)
    }
    val icon = when (callType) {
        CallType.CELLULAR -> Icons.Default.Phone
        else -> Icons.Default.Wifi
    }
    Box(
        modifier          = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment  = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = YacrOnBackground, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun EncryptedBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(YacrEncryptedBadge)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text  = "🔒 AES-256",
            style = MaterialTheme.typography.labelMedium,
            color = YacrAccentGreen,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun RecordingIndicatorDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue  = 0.85f,
        targetValue   = 1.15f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label         = "pulse_scale"
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(YacrPrimary)
    )
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = YacrPrimary)
    }
}

@Composable
private fun EmptyState(isSearchActive: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isSearchActive) Icons.Default.SearchOff else Icons.Default.PhoneDisabled,
                contentDescription = null,
                tint     = YacrOnSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text  = if (isSearchActive) "No results found" else "No recordings yet",
                style = MaterialTheme.typography.titleMedium,
                color = YacrOnSurfaceVariant
            )
            if (!isSearchActive) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = "YACR will automatically record calls\nonce permissions are granted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = YacrOnSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Formatters
// ─────────────────────────────────────────────────────────────────────────────

private fun formatTimestamp(ms: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - ms
    return when {
        diffMs < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diffMs < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diffMs)}m ago"
        diffMs < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diffMs)}h ago"
        diffMs < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diffMs)}d ago"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ms))
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val mins  = (totalSec % 3600) / 60
    val secs  = totalSec % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, mins, secs)
    else "%d:%02d".format(mins, secs)
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
}
