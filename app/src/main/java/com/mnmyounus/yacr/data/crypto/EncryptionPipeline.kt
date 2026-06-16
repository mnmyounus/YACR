/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  data/crypto/EncryptionPipeline.kt       ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ║                                                                              ║
 * ║  Streaming AES-GCM-256 encryption/decryption pipeline.                     ║
 * ║                                                                              ║
 * ║  DESIGN:                                                                     ║
 * ║   AES-GCM in standard mode is not natively stream-oriented (the GCM tag     ║
 * ║   is appended at the end). For streaming audio capture, YACR uses a        ║
 * ║   "chunked GCM" approach:                                                   ║
 * ║                                                                              ║
 * ║   ┌────────────────────────────────────────────────────────────────────┐    ║
 * ║   │  FILE FORMAT (.yacr):                                              │    ║
 * ║   │  [MAGIC  4B][VERSION 2B][SAMPLE_RATE 4B][CHANNELS 2B][BIT_DEPTH 2B]│   ║
 * ║   │  Then for each audio chunk:                                        │    ║
 * ║   │    [IV 12B][CIPHERTEXT_LEN 4B][CIPHERTEXT (data + 16B GCM tag)]    │    ║
 * ║   └────────────────────────────────────────────────────────────────────┘    ║
 * ║                                                                              ║
 * ║   Each chunk uses a fresh random 96-bit IV. This satisfies AES-GCM's       ║
 * ║   requirement for unique nonces while enabling streaming writes.            ║
 * ║   A 128-bit GCM authentication tag is appended per-chunk, providing        ║
 * ║   both confidentiality and per-chunk integrity verification.                ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.data.crypto

import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionPipeline @Inject constructor(
    private val keystoreManager: KeystoreManager
) {
    companion object {
        /** 4-byte magic number identifying a YACR encrypted audio file. */
        val FILE_MAGIC = byteArrayOf(0x59, 0x41, 0x43, 0x52) // "YACR"

        /** Current file format version. */
        const val FORMAT_VERSION: Short = 1

        /** File extension for YACR encrypted recordings. */
        const val FILE_EXTENSION = ".yacr"

        /** Size of the fixed file header in bytes. */
        const val HEADER_SIZE_BYTES = 4 + 2 + 4 + 2 + 2  // magic + version + sampleRate + channels + bitDepth

        /** GCM overhead per chunk: IV (12) + length field (4) + GCM tag (16) = 32 bytes */
        const val PER_CHUNK_OVERHEAD = KeystoreManager.GCM_IV_LENGTH_BYTES + 4 + (KeystoreManager.GCM_TAG_LENGTH_BITS / 8)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Encrypted Output Stream
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a streaming [EncryptingOutputStream] that transparently encrypts
     * audio buffers with fresh per-chunk GCM ciphers.
     *
     * @param outputFile    Destination file on disk (will be overwritten if exists).
     * @param sampleRate    PCM sample rate in Hz (e.g. 44100).
     * @param channels      Number of audio channels (1 = mono, 2 = stereo).
     * @param bitDepth      PCM bit depth (e.g. 16).
     */
    fun createEncryptingStream(
        outputFile: File,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int
    ): EncryptingOutputStream {
        keystoreManager.ensureKeyExists()

        val rawOutputStream = outputFile.outputStream().buffered(65536)

        // Write the file header
        rawOutputStream.write(FILE_MAGIC)
        rawOutputStream.write(ByteBuffer.allocate(2).putShort(FORMAT_VERSION).array())
        rawOutputStream.write(ByteBuffer.allocate(4).putInt(sampleRate).array())
        rawOutputStream.write(ByteBuffer.allocate(2).putShort(channels.toShort()).array())
        rawOutputStream.write(ByteBuffer.allocate(2).putShort(bitDepth.toShort()).array())
        rawOutputStream.flush()

        Timber.d(
            "EncryptionPipeline: Opened encrypted stream → ${outputFile.name} " +
                    "(${sampleRate}Hz, ${channels}ch, ${bitDepth}bit)"
        )

        return EncryptingOutputStream(rawOutputStream, keystoreManager)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Decryption
    // ─────────────────────────────────────────────────────────────────════════

    /**
     * Decrypt an entire YACR-encrypted file to a plain PCM [OutputStream].
     * For playback, wrap the output in a WAV container via [decryptToWav].
     *
     * @param encryptedFile Source .yacr file.
     * @param outputStream  Destination stream receiving raw PCM bytes.
     * @return [AudioMetadata] parsed from the file header.
     */
    fun decryptStream(
        encryptedFile: File,
        outputStream: OutputStream
    ): AudioMetadata {
        require(encryptedFile.exists()) {
            "Encrypted file not found: ${encryptedFile.absolutePath}"
        }

        val inputStream = encryptedFile.inputStream().buffered(65536)
        inputStream.use { stream ->
            // Validate magic number
            val magic = stream.readNBytes(FILE_MAGIC.size)
            require(magic.contentEquals(FILE_MAGIC)) {
                "Invalid file magic. Not a YACR encrypted file."
            }

            // Read header
            val version    = ByteBuffer.wrap(stream.readNBytes(2)).short
            val sampleRate = ByteBuffer.wrap(stream.readNBytes(4)).int
            val channels   = ByteBuffer.wrap(stream.readNBytes(2)).short.toInt()
            val bitDepth   = ByteBuffer.wrap(stream.readNBytes(2)).short.toInt()

            Timber.d(
                "EncryptionPipeline: Decrypting v$version file | " +
                        "${sampleRate}Hz / ${channels}ch / ${bitDepth}bit"
            )

            var chunksDecrypted = 0
            var totalBytesDecrypted = 0L

            // Decrypt chunk-by-chunk until EOF
            while (true) {
                val ivBytes = stream.readNBytesOrNull(KeystoreManager.GCM_IV_LENGTH_BYTES)
                    ?: break // clean EOF

                val ciphertextLenBytes = stream.readNBytes(4)
                val ciphertextLen = ByteBuffer.wrap(ciphertextLenBytes).order(ByteOrder.BIG_ENDIAN).int

                if (ciphertextLen <= 0 || ciphertextLen > 10 * 1024 * 1024) {
                    Timber.e("EncryptionPipeline: Invalid chunk length $ciphertextLen — aborting decryption")
                    break
                }

                val ciphertext = stream.readNBytes(ciphertextLen)

                val cipher = keystoreManager.createDecryptionCipher(ivBytes)
                val plaintext = cipher.doFinal(ciphertext)

                outputStream.write(plaintext)
                chunksDecrypted++
                totalBytesDecrypted += plaintext.size
            }

            outputStream.flush()
            Timber.d(
                "EncryptionPipeline: Decryption complete — $chunksDecrypted chunks, " +
                        "${totalBytesDecrypted / 1024}KB plaintext PCM"
            )

            return AudioMetadata(sampleRate, channels, bitDepth, totalBytesDecrypted)
        }
    }

    /**
     * Decrypt to a temporary WAV file suitable for playback with ExoPlayer or MediaPlayer.
     *
     * @param encryptedFile Source .yacr file.
     * @param outputFile    Destination .wav file (will be overwritten).
     */
    fun decryptToWav(encryptedFile: File, outputFile: File) {
        // Decrypt PCM into a buffer first (we need byte count for WAV header)
        val pcmBuffer = java.io.ByteArrayOutputStream(4 * 1024 * 1024) // pre-alloc 4MB
        val metadata = decryptStream(encryptedFile, pcmBuffer)
        val pcmBytes = pcmBuffer.toByteArray()

        outputFile.outputStream().buffered().use { out ->
            writeWavHeader(out, metadata, pcmBytes.size)
            out.write(pcmBytes)
        }

        Timber.d(
            "EncryptionPipeline: WAV file written → ${outputFile.name} " +
                    "(${pcmBytes.size / 1024}KB PCM + 44B header)"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WAV Header Writer
    // ─────────────────────────────────────────────────────────────────────────

    private fun writeWavHeader(out: OutputStream, meta: AudioMetadata, pcmDataSizeBytes: Int) {
        val byteRate = meta.sampleRate * meta.channels * (meta.bitDepth / 8)
        val blockAlign = meta.channels * (meta.bitDepth / 8)
        val totalDataLen = pcmDataSizeBytes + 36

        fun Int.toLeBytes(): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(this).array()
        fun Short.toLeBytes(): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(this).array()

        out.write("RIFF".toByteArray())
        out.write(totalDataLen.toLeBytes())
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(16.toLeBytes())                          // PCM chunk size
        out.write((1.toShort()).toLeBytes())               // PCM format = 1
        out.write(meta.channels.toShort().toLeBytes())
        out.write(meta.sampleRate.toLeBytes())
        out.write(byteRate.toLeBytes())
        out.write(blockAlign.toShort().toLeBytes())
        out.write(meta.bitDepth.toShort().toLeBytes())
        out.write("data".toByteArray())
        out.write(pcmDataSizeBytes.toLeBytes())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────────

    data class AudioMetadata(
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,
        val pcmSizeBytes: Long
    )

    // ─────────────────────────────────────────────────────────────────────────
    // InputStream Extension
    // ─────────────────────────────────────────────────────────────────────────

    private fun InputStream.readNBytesOrNull(n: Int): ByteArray? {
        val buf = ByteArray(n)
        var totalRead = 0
        while (totalRead < n) {
            val read = read(buf, totalRead, n - totalRead)
            if (read == -1) return if (totalRead == 0) null else null // partial = invalid
            totalRead += read
        }
        return buf
    }
}
