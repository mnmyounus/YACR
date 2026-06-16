package com.mnmyounus.yacr.domain.usecase

import com.mnmyounus.yacr.domain.repository.RecordingRepository
import java.io.File
import javax.inject.Inject

/**
 * Use case: Decrypt a recording to a temporary file for playback or export.
 * Callers MUST delete the output file when done to avoid plaintext on disk.
 */
class DecryptRecordingUseCase @Inject constructor(
    private val repository: RecordingRepository
) {
    /**
     * @param id         Recording UUID.
     * @param outputFile Writable file to receive decrypted audio.
     */
    suspend operator fun invoke(id: String, outputFile: File) =
        repository.decryptToTemp(id, outputFile)
}
