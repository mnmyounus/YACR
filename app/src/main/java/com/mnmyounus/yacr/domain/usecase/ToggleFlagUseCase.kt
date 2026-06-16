package com.mnmyounus.yacr.domain.usecase

import com.mnmyounus.yacr.domain.repository.RecordingRepository
import javax.inject.Inject

/** Use case: Toggle the starred/flagged state of a recording. */
class ToggleFlagUseCase @Inject constructor(
    private val repository: RecordingRepository
) {
    suspend operator fun invoke(id: String) = repository.toggleFlag(id)
}
