/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  data/local/datastore/YACRPreferences.kt ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "yacr_preferences")

/**
 * Type-safe DataStore preferences accessor for YACR settings.
 * All reads return [Flow]s for reactive UI binding.
 */
@Singleton
class YACRPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ─── Keys ────────────────────────────────────────────────────────────────

    private object Keys {
        val AUTO_RECORD_ENABLED       = booleanPreferencesKey("auto_record_enabled")
        val RECORD_CELLULAR           = booleanPreferencesKey("record_cellular")
        val RECORD_VOIP               = booleanPreferencesKey("record_voip")
        val AUDIO_SOURCE              = intPreferencesKey("audio_source")
        val SAMPLE_RATE               = intPreferencesKey("sample_rate")
        val BIT_RATE                  = intPreferencesKey("bit_rate")
        val BIOMETRIC_LOCK            = booleanPreferencesKey("biometric_lock")
        val AUTO_DELETE_AFTER_DAYS    = intPreferencesKey("auto_delete_days")
        val SHOW_RECORDING_INDICATOR  = booleanPreferencesKey("show_recording_indicator")
        val FIRST_LAUNCH_DONE         = booleanPreferencesKey("first_launch_done")
        val TOTAL_RECORDINGS_CREATED  = longPreferencesKey("total_recordings_created")
        val STORAGE_PATH_OVERRIDE     = stringPreferencesKey("storage_path_override")
    }

    // ─── Reads ────────────────────────────────────────────────────────────────

    val autoRecordEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_RECORD_ENABLED] ?: true
    }

    val recordCellular: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.RECORD_CELLULAR] ?: true
    }

    val recordVoip: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.RECORD_VOIP] ?: true
    }

    val audioSource: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUDIO_SOURCE] ?: android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
    }

    val sampleRate: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.SAMPLE_RATE] ?: 44100
    }

    val biometricLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.BIOMETRIC_LOCK] ?: false
    }

    val autoDeleteAfterDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_DELETE_AFTER_DAYS] ?: 0  // 0 = never
    }

    val showRecordingIndicator: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_RECORDING_INDICATOR] ?: true
    }

    val firstLaunchDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.FIRST_LAUNCH_DONE] ?: false
    }

    // ─── Writes ────────────────────────────────────────────────────────────

    suspend fun setAutoRecordEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_RECORD_ENABLED] = enabled }

    suspend fun setRecordCellular(enabled: Boolean) =
        context.dataStore.edit { it[Keys.RECORD_CELLULAR] = enabled }

    suspend fun setRecordVoip(enabled: Boolean) =
        context.dataStore.edit { it[Keys.RECORD_VOIP] = enabled }

    suspend fun setAudioSource(source: Int) =
        context.dataStore.edit { it[Keys.AUDIO_SOURCE] = source }

    suspend fun setSampleRate(rate: Int) =
        context.dataStore.edit { it[Keys.SAMPLE_RATE] = rate }

    suspend fun setBiometricLock(enabled: Boolean) =
        context.dataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }

    suspend fun setAutoDeleteAfterDays(days: Int) =
        context.dataStore.edit { it[Keys.AUTO_DELETE_AFTER_DAYS] = days }

    suspend fun setFirstLaunchDone() =
        context.dataStore.edit { it[Keys.FIRST_LAUNCH_DONE] = true }

    suspend fun incrementTotalRecordingsCreated() {
        context.dataStore.edit { prefs ->
            prefs[Keys.TOTAL_RECORDINGS_CREATED] = (prefs[Keys.TOTAL_RECORDINGS_CREATED] ?: 0L) + 1
        }
    }
}
