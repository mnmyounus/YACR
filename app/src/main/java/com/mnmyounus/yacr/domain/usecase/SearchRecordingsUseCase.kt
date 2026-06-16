package com.mnmyounus.yacr.domain.usecase

import com.mnmyounus.yacr.domain.model.Recording
import com.mnmyounus.yacr.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case: Search recordings by caller name or phone number.
 */
class SearchRecordingsUseCase @Inject constructor(
    private val repository: RecordingRepository
) {
    operator fun invoke(query: String): Flow<List<Recording>> =
        if (query.isBlank()) repository.observeAllRecordings()
        else repository.searchRecordings(query.trim())
}
