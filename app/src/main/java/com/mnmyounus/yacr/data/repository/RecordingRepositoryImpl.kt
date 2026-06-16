/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  data/repository/RecordingRepositoryImpl ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.data.repository

import com.mnmyounus.yacr.data.crypto.EncryptionPipeline
import com.mnmyounus.yacr.data.local.database.dao.RecordingDao
import com.mnmyounus.yacr.data.local.database.entity.RecordingEntity
import com.mnmyounus.yacr.domain.model.Recording
import com.mnmyounus.yacr.domain.repository.RecordingRepository
import com.mnmyounus.yacr.domain.repository.StorageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepositoryImpl @Inject constructor(
    private val dao: RecordingDao,
    private val encryptionPipeline: EncryptionPipeline
) : RecordingRepository {

    override fun observeAllRecordings(): Flow<List<Recording>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun searchRecordings(query: String): Flow<List<Recording>> =
        dao.search(query).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getRecordingById(id: String): Recording? =
        withContext(Dispatchers.IO) {
            dao.getById(id)?.toDomain()
        }

    override suspend fun saveRecording(recording: Recording) =
        withContext(Dispatchers.IO) {
            dao.insert(RecordingEntity.fromDomain(recording))
            Timber.d("RecordingRepository: Saved recording ${recording.id} — ${recording.callerName}")
        }

    override suspend fun toggleFlag(id: String) =
        withContext(Dispatchers.IO) {
            dao.toggleFlag(id)
        }

    override suspend fun deleteRecording(id: String): Boolean =
        withContext(Dispatchers.IO) {
            val filePath = dao.getFilePath(id)
            val rowsDeleted = dao.deleteById(id)

            if (rowsDeleted > 0 && filePath != null) {
                val file = File(filePath)
                val fileDeleted = if (file.exists()) {
                    file.delete().also { success ->
                        if (!success) Timber.w("RecordingRepository: Could not delete file $filePath")
                    }
                } else {
                    Timber.w("RecordingRepository: File not found for deletion: $filePath")
                    true // DB row deleted; file already gone
                }
                Timber.d("RecordingRepository: Deleted recording $id (file: $fileDeleted)")
                return@withContext fileDeleted
            }

            Timber.w("RecordingRepository: No recording found with id $id")
            false
        }

    override suspend fun deleteRecordings(ids: List<String>): Int =
        withContext(Dispatchers.IO) {
            var deletedCount = 0
            ids.forEach { id ->
                if (deleteRecording(id)) deletedCount++
            }
            deletedCount
        }

    override suspend fun decryptToTemp(id: String, outputFile: File) =
        withContext(Dispatchers.IO) {
            val recording = dao.getById(id)
                ?: throw IllegalArgumentException("Recording $id not found")

            val encryptedFile = File(recording.encryptedFilePath)
            if (!encryptedFile.exists()) {
                throw java.io.FileNotFoundException(
                    "Encrypted file missing: ${recording.encryptedFilePath}"
                )
            }

            Timber.d("RecordingRepository: Decrypting ${recording.id} → ${outputFile.name}")
            encryptionPipeline.decryptToWav(encryptedFile, outputFile)
        }

    override suspend fun getStorageSummary(): StorageSummary =
        withContext(Dispatchers.IO) {
            StorageSummary(
                totalCount          = dao.getCount(),
                totalEncryptedBytes = dao.getTotalFileSizeBytes() ?: 0L
            )
        }

    override fun getTotalRecordingCount(): Flow<Int> = dao.observeCount()
}
