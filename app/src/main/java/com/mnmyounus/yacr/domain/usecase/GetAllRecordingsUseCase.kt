package com.mnmyounus.yacr.domain.usecase

import com.mnmyounus.yacr.domain.model.Recording
import com.mnmyounus.yacr.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case: Observe all recordings in real time.
 * Emits a fresh list on every database write — ideal for LiveData/StateFlow binding.
 */
class GetAllRecordingsUseCase @Inject constructor(
    private val repository: RecordingRepository
) {
    operator fun invoke(): Flow<List<Recording>> =
        repository.observeAllRecordings()
}
