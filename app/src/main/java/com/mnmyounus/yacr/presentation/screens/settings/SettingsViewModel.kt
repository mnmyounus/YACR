/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  presentation/screens/settings/          ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnmyounus.yacr.data.crypto.KeystoreManager
import com.mnmyounus.yacr.data.local.datastore.YACRPreferences
import com.mnmyounus.yacr.domain.repository.RecordingRepository
import com.mnmyounus.yacr.service.YACRAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: YACRPreferences,
    private val keystoreManager: KeystoreManager,
    private val repository: RecordingRepository
) : ViewModel() {

    data class UiState(
        val autoRecordEnabled: Boolean = true,
        val recordCellular: Boolean    = true,
        val recordVoip: Boolean        = true,
        val biometricLock: Boolean     = false,
        val autoDeleteDays: Int        = 0,
        val isAccessibilityEnabled: Boolean = false,
        val isKeystoreHardwareBacked: Boolean = false,
        val totalRecordings: Int       = 0,
        val storageUsedMb: Float       = 0f,
        val appVersion: String         = "",
        val snackbarMessage: String?   = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        loadPreferences()
        loadSystemInfo()
        loadStorageStats()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            combine(
                preferences.autoRecordEnabled,
                preferences.recordCellular,
                preferences.recordVoip,
                preferences.biometricLockEnabled,
                preferences.autoDeleteAfterDays
            ) { auto, cell, voip, bio, days ->
                _uiState.update { state ->
                    state.copy(
                        autoRecordEnabled = auto,
                        recordCellular    = cell,
                        recordVoip        = voip,
                        biometricLock     = bio,
                        autoDeleteDays    = days
                    )
                }
            }.collect {}
        }
    }

    private fun loadSystemInfo() {
        _uiState.update { state ->
            state.copy(
                isAccessibilityEnabled   = YACRAccessibilityService.isAccessibilityServiceEnabled(context),
                isKeystoreHardwareBacked = try { keystoreManager.isKeyHardwareBacked() } catch (e: Exception) { false },
                appVersion               = try {
                    context.packageManager
                        .getPackageInfo(context.packageName, 0).versionName ?: "—"
                } catch (e: Exception) { "—" }
            )
        }
    }

    private fun loadStorageStats() {
        viewModelScope.launch {
            try {
                val summary = repository.getStorageSummary()
                _uiState.update { state ->
                    state.copy(
                        totalRecordings = summary.totalCount,
                        storageUsedMb   = summary.totalEncryptedBytes / (1024f * 1024f)
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "SettingsViewModel: Failed to load storage stats")
            }
        }
    }

    fun onAutoRecordToggle(enabled: Boolean) = viewModelScope.launch {
        preferences.setAutoRecordEnabled(enabled)
    }
    fun onRecordCellularToggle(enabled: Boolean) = viewModelScope.launch {
        preferences.setRecordCellular(enabled)
    }
    fun onRecordVoipToggle(enabled: Boolean) = viewModelScope.launch {
        preferences.setRecordVoip(enabled)
    }
    fun onBiometricLockToggle(enabled: Boolean) = viewModelScope.launch {
        preferences.setBiometricLock(enabled)
    }
    fun onAutoDeleteDaysChanged(days: Int) = viewModelScope.launch {
        preferences.setAutoDeleteAfterDays(days)
    }

    fun refreshAccessibilityStatus() {
        _uiState.update { it.copy(isAccessibilityEnabled = YACRAccessibilityService.isAccessibilityServiceEnabled(context)) }
    }

    fun onSnackbarDismissed() = _uiState.update { it.copy(snackbarMessage = null) }
}
