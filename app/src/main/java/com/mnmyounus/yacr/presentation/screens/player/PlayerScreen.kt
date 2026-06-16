/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  presentation/screens/player/PlayerScreen║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.presentation.screens.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mnmyounus.yacr.domain.model.CallType
import com.mnmyounus.yacr.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    recordingId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = YacrOnBackground)
                    }
                },
                title = { Text("Recording", fontWeight = FontWeight.SemiBold, color = YacrOnBackground) },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = YacrOnSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = YacrBackground)
            )
        },
        containerColor = YacrBackground
    ) { padding ->
        Box(
            modifier          = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment  = Alignment.Center
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(color = YacrPrimary)
                uiState.error != null -> ErrorState(message = uiState.error!!, onBack = onBack)
                uiState.recording != null -> PlayerContent(
                    uiState  = uiState,
                    onPlayPause   = viewModel::onPlayPause,
                    onSeek        = viewModel::onSeek,
                    onSkipFwd     = viewModel::onSkipForward,
                    onSkipBack    = viewModel::onSkipBackward
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete recording?", color = YacrOnBackground) },
            text  = { Text("This action is permanent and cannot be undone.", color = YacrOnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.onDelete { onBack() } }) {
                    Text("Delete", color = YacrPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = YacrOnSurfaceVariant)
                }
            },
            containerColor = YacrSurface
        )
    }
}

@Composable
private fun PlayerContent(
    uiState: PlayerViewModel.UiState,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSkipFwd: () -> Unit,
    onSkipBack: () -> Unit
) {
    val recording = uiState.recording!!
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Caller Avatar ──────────────────────────────────────────────────
        Box(
            modifier         = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(YacrSurfaceVariant, YacrSecondary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (recording.callType.isCellular) Icons.Default.Phone else Icons.Default.Wifi,
                contentDescription = null,
                tint   = YacrOnBackground,
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Caller Info ────────────────────────────────────────────────────
        Text(
            text       = recording.callerName,
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color      = YacrOnBackground,
            textAlign  = TextAlign.Center,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )
        if (recording.phoneNumber != recording.callerName) {
            Text(
                text  = recording.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = YacrOnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text  = "${formatCallType(recording.callType)} • ${formatDate(recording.startTimestampMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = YacrOnSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        // Encrypted badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(YacrEncryptedBadge)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("🔒  AES-GCM-256 Encrypted", fontSize = 11.sp, color = YacrAccentGreen, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.weight(1f))

        // ── Waveform placeholder / progress ───────────────────────────────
        if (uiState.isDecrypting) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = YacrPrimary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                Text("Decrypting…", style = MaterialTheme.typography.bodySmall, color = YacrOnSurfaceVariant)
            }
        } else {
            Slider(
                value     = uiState.progress,
                onValueChange = onSeek,
                colors    = SliderDefaults.colors(
                    thumbColor        = YacrPrimary,
                    activeTrackColor  = YacrPrimary,
                    inactiveTrackColor = YacrOutline
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMs(uiState.positionMs), style = MaterialTheme.typography.bodySmall, color = YacrOnSurfaceVariant)
                Text(formatMs(uiState.durationMs), style = MaterialTheme.typography.bodySmall, color = YacrOnSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Transport Controls ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSkipBack, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = YacrOnSurface, modifier = Modifier.size(32.dp))
            }
            FilledIconButton(
                onClick   = onPlayPause,
                modifier  = Modifier.size(72.dp),
                enabled   = !uiState.isDecrypting,
                colors    = IconButtonDefaults.filledIconButtonColors(containerColor = YacrPrimary)
            ) {
                Icon(
                    if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(38.dp),
                    tint = YacrOnPrimary
                )
            }
            IconButton(onClick = onSkipFwd, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = YacrOnSurface, modifier = Modifier.size(32.dp))
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Playback Error", style = MaterialTheme.typography.titleMedium, color = YacrOnBackground)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = YacrOnSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = YacrPrimary)) {
            Text("Go Back")
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%d:%02d".format(mins, secs)
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

private fun formatCallType(type: CallType): String = when (type) {
    CallType.CELLULAR -> "Cellular"
    CallType.WHATSAPP -> "WhatsApp"
    CallType.SIGNAL   -> "Signal"
    CallType.TELEGRAM -> "Telegram"
    CallType.VIBER    -> "Viber"
    CallType.MESSENGER -> "Messenger"
    CallType.SKYPE    -> "Skype"
    CallType.GOOGLE_MEET -> "Google Meet"
    CallType.ZOOM     -> "Zoom"
    CallType.VOIP_OTHER -> "VoIP"
}
