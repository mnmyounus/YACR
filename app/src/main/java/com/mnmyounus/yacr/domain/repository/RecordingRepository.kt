/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  domain/repository/RecordingRepository.kt ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.domain.repository

import com.mnmyounus.yacr.domain.model.Recording
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Repository contract for the Recording domain.
 * The domain layer depends only on this interface — never on the concrete implementation.
 * This enables easy substitution for testing via fakes/mocks.
 */
interface RecordingRepository {

    /**
     * Observe all recordings, ordered by start timestamp descending (newest first).
     * Emits a new list whenever the underlying data changes.
     */
    fun observeAllRecordings(): Flow<List<Recording>>

    /**
     * Observe recordings matching the given search query (caller name or number).
     */
    fun searchRecordings(query: String): Flow<List<Recording>>

    /**
     * Fetch a single recording by its UUID. Returns null if not found.
     */
    suspend fun getRecordingById(id: String): Recording?

    /**
     * Persist a completed recording to the database.
     */
    suspend fun saveRecording(recording: Recording)

    /**
     * Toggle the flagged/starred state of a recording.
     */
    suspend fun toggleFlag(id: String)

    /**
     * Permanently delete a recording record AND its encrypted file from disk.
     * Returns true if both the DB row and file were successfully removed.
     */
    suspend fun deleteRecording(id: String): Boolean

    /**
     * Delete multiple recordings. Returns the count successfully deleted.
     */
    suspend fun deleteRecordings(ids: List<String>): Int

    /**
     * Decrypt a recording to a temporary [File] for playback or export.
     * The caller is responsible for deleting the file after use.
     *
     * @param id          Recording UUID to decrypt.
     * @param outputFile  Destination file for the decrypted PCM/WAV data.
     * @throws SecurityException if decryption fails or key is unavailable.
     */
    suspend fun decryptToTemp(id: String, outputFile: File)

    /**
     * Returns total number of recordings and aggregate encrypted file size.
     */
    suspend fun getStorageSummary(): StorageSummary

    /** Total number of recordings in the database. */
    fun getTotalRecordingCount(): Flow<Int>
}

/** Summary data for storage analytics. */
data class StorageSummary(
    val totalCount: Int,
    val totalEncryptedBytes: Long
)
