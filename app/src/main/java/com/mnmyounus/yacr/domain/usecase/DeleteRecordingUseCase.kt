package com.mnmyounus.yacr.domain.usecase

import com.mnmyounus.yacr.domain.repository.RecordingRepository
import javax.inject.Inject

/**
 * Use case: Delete one or more recordings (database row + encrypted file).
 */
class DeleteRecordingUseCase @Inject constructor(
    private val repository: RecordingRepository
) {
    /** Delete a single recording. Returns true on success. */
    suspend operator fun invoke(id: String): Boolean =
        repository.deleteRecording(id)

    /** Delete multiple recordings. Returns count deleted. */
    suspend fun deleteMany(ids: List<String>): Int =
        repository.deleteRecordings(ids)
}
