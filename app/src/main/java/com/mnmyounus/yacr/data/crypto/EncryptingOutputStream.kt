/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  data/crypto/EncryptingOutputStream.kt   ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ║                                                                              ║
 * ║  A write-only OutputStream that encrypts each audio buffer chunk with a     ║
 * ║  fresh AES-GCM-256 cipher before writing to the underlying disk stream.     ║
 * ║                                                                              ║
 * ║  Thread-safety: This class is NOT thread-safe. It must be used from a       ║
 * ║  single thread (the AudioRecord capture thread).                            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.data.crypto

import timber.log.Timber
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EncryptingOutputStream(
    private val rawOut: OutputStream,
    private val keystoreManager: KeystoreManager
) : OutputStream() {

    private var closed = false
    private var chunksWritten = 0L
    private var bytesEncrypted = 0L

    /**
     * Encrypt [len] bytes from [b] starting at [off] and write the result
     * (IV + length + ciphertext) to the underlying stream.
     *
     * This is the hot path — called once per AudioRecord buffer read.
     * Allocations are minimized for low GC pressure during recording.
     */
    override fun write(b: ByteArray, off: Int, len: Int) {
        check(!closed) { "EncryptingOutputStream is closed" }
        if (len == 0) return

        val plaintext = if (off == 0 && len == b.size) b else b.copyOfRange(off, off + len)

        // Get a fresh encryption cipher (generates a new random IV internally)
        val cipher = keystoreManager.createEncryptionCipher()
        val iv = cipher.iv  // 12-byte GCM nonce

        // Encrypt the plaintext — AES/GCM/NoPadding appends the 16-byte tag
        val ciphertext = cipher.doFinal(plaintext)

        // Write: [IV 12B][ciphertext_len 4B][ciphertext]
        rawOut.write(iv)
        rawOut.write(
            ByteBuffer.allocate(4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(ciphertext.size)
                .array()
        )
        rawOut.write(ciphertext)

        chunksWritten++
        bytesEncrypted += plaintext.size
    }

    /** Single-byte write — delegates to the bulk write above. */
    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun flush() {
        check(!closed) { "EncryptingOutputStream is closed" }
        rawOut.flush()
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            rawOut.flush()
            rawOut.close()
        } finally {
            Timber.d(
                "EncryptingOutputStream: Closed — $chunksWritten chunks, " +
                        "${bytesEncrypted / 1024}KB plaintext encrypted"
            )
        }
    }

    /** @return Total plaintext bytes encrypted so far. */
    val totalBytesEncrypted: Long get() = bytesEncrypted
}
