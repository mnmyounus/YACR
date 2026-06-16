/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  presentation/screens/settings/          ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.presentation.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mnmyounus.yacr.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.onSnackbarDismissed()
        }
    }

    // Refresh accessibility status when screen is visible
    LaunchedEffect(Unit) { viewModel.refreshAccessibilityStatus() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = YacrOnBackground)
                    }
                },
                title = { Text("Settings", fontWeight = FontWeight.SemiBold, color = YacrOnBackground) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = YacrBackground)
            )
        },
        containerColor = YacrBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Recording Behavior ─────────────────────────────────────────
            SettingsSection(title = "Recording") {
                ToggleSetting(
                    icon = Icons.Default.FiberManualRecord,
                    title = "Auto-record all calls",
                    subtitle = "Start recording automatically when a call is detected",
                    checked = uiState.autoRecordEnabled,
                    onCheckedChange = viewModel::onAutoRecordToggle
                )
                ToggleSetting(
                    icon = Icons.Default.Phone,
                    title = "Cellular calls",
                    subtitle = "Record standard phone calls",
                    checked = uiState.recordCellular,
                    onCheckedChange = viewModel::onRecordCellularToggle
                )
                ToggleSetting(
                    icon = Icons.Default.Wifi,
                    title = "VoIP / IP calls",
                    subtitle = "WhatsApp, Signal, Telegram, Viber, etc.",
                    checked = uiState.recordVoip,
                    onCheckedChange = viewModel::onRecordVoipToggle
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Accessibility Service ──────────────────────────────────────
            SettingsSection(title = "VoIP Detection") {
                val accessibilityBgColor = if (uiState.isAccessibilityEnabled)
                    YacrEncryptedBadge else MaterialTheme.colorScheme.errorContainer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(accessibilityBgColor)
                        .clickable {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (uiState.isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (uiState.isAccessibilityEnabled) YacrAccentGreen else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "YACR VoIP Monitor",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = YacrOnBackground
                        )
                        Text(
                            if (uiState.isAccessibilityEnabled) "Active — VoIP calls will be detected"
                            else "Tap to enable in Accessibility Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = YacrOnSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = YacrOnSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Security ──────────────────────────────────────────────────
            SettingsSection(title = "Security") {
                InfoRow(
                    icon  = Icons.Default.Lock,
                    title = "Encryption",
                    value = "AES-GCM-256"
                )
                InfoRow(
                    icon  = Icons.Default.Security,
                    title = "Key storage",
                    value = if (uiState.isKeystoreHardwareBacked) "Hardware-backed (TEE)" else "Software Keystore"
                )
                InfoRow(
                    icon  = Icons.Default.CloudOff,
                    title = "Network access",
                    value = "None — Zero Internet policy"
                )
                ToggleSetting(
                    icon = Icons.Default.Fingerprint,
                    title = "Biometric app lock",
                    subtitle = "Require fingerprint/face to open YACR",
                    checked = uiState.biometricLock,
                    onCheckedChange = viewModel::onBiometricLockToggle
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Storage ────────────────────────────────────────────────────
            SettingsSection(title = "Storage") {
                InfoRow(
                    icon  = Icons.Default.Storage,
                    title = "Total recordings",
                    value = "${uiState.totalRecordings}"
                )
                InfoRow(
                    icon  = Icons.Default.Folder,
                    title = "Encrypted storage used",
                    value = "${"%.2f".format(uiState.storageUsedMb)} MB"
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── About ─────────────────────────────────────────────────────
            SettingsSection(title = "About") {
                InfoRow(icon = Icons.Default.Info,      title = "Version",   value = uiState.appVersion)
                InfoRow(icon = Icons.Default.Person,    title = "Developer", value = "MNM YOUNUS")
                InfoRow(icon = Icons.Default.Apps,      title = "App",       value = "YACR – Your All Call Recorder")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable Setting Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelMedium,
        color    = YacrPrimary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        letterSpacing = 1.sp
    )
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = YacrCardSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            content()
        }
    }
}

@Composable
private fun ToggleSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = YacrOnSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = YacrOnBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = YacrOnSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor  = YacrOnPrimary,
                checkedTrackColor  = YacrPrimary,
                uncheckedThumbColor = YacrOnSurfaceVariant,
                uncheckedTrackColor = YacrSecondary
            )
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = YacrOnSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = YacrOnBackground, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = YacrOnSurfaceVariant)
    }
}
