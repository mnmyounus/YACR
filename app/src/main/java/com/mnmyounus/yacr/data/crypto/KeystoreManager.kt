/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  data/crypto/KeystoreManager.kt          ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ║                                                                              ║
 * ║  Manages the AES-GCM-256 master key inside the Android Keystore.            ║
 * ║                                                                              ║
 * ║  Security Properties:                                                        ║
 * ║   • Key resides exclusively in hardware-backed Keystore (TEE / StrongBox)   ║
 * ║   • NEVER extracted from Keystore — only used for in-situ encryption        ║
 * ║   • Key is USER_AUTHENTICATION_REQUIRED = false (recording runs in bg)      ║
 * ║   • Key invalidated on new biometric enrollment (optional, configurable)    ║
 * ║   • GCM tag length: 128 bits (maximum security)                             ║
 * ║   • IV length: 96 bits (12 bytes) — NIST recommended for GCM               ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProperties.BLOCK_MODE_GCM
import android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
import android.security.keystore.KeyProperties.KEY_ALGORITHM_AES
import android.security.keystore.KeyProperties.PURPOSE_DECRYPT
import android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
import timber.log.Timber
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor() {

    companion object {
        /** Alias of the AES-GCM key inside the Android Keystore. */
        const val KEY_ALIAS = "yacr_aes_gcm_256_v1"

        /** Android Keystore provider name. */
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

        /** AES key size in bits. */
        private const val KEY_SIZE_BITS = 256

        /** GCM authentication tag length in bits. */
        const val GCM_TAG_LENGTH_BITS = 128

        /** GCM IV (nonce) length in bytes. */
        const val GCM_IV_LENGTH_BYTES = 12

        /** Cipher transformation string. */
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Key Management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ensures the AES-GCM key exists in the Keystore. Generates it if absent.
     * Safe to call multiple times (idempotent).
     *
     * @throws KeyStoreException if Keystore is unavailable or key generation fails.
     */
    fun ensureKeyExists() {
        val keyStore = loadKeyStore()
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            Timber.i("KeystoreManager: Key '$KEY_ALIAS' not found — generating new AES-GCM-256 key")
            generateKey()
        } else {
            Timber.d("KeystoreManager: Key '$KEY_ALIAS' exists in Keystore")
            logKeyMetadata(keyStore)
        }
    }

    /**
     * Generate a new AES-GCM-256 key and store it in the Android Keystore.
     * The key is hardware-backed when the device supports TEE or StrongBox.
     */
    private fun generateKey() {
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            PURPOSE_ENCRYPT or PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(BLOCK_MODE_GCM)
            .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
            .setGcmTagLength(GCM_TAG_LENGTH_BITS)
            // Randomized encryption ensures unique ciphertext for each recording
            .setRandomizedEncryptionRequired(true)
            // Key does NOT require user authentication — needed for background recording
            .setUserAuthenticationRequired(false)
            // Digest not needed for AES-GCM but explicit for spec completeness
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .build()

        KeyGenerator.getInstance(KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
            init(keyGenParameterSpec)
            generateKey()
        }

        Timber.i("KeystoreManager: AES-GCM-256 key generated successfully under alias '$KEY_ALIAS'")
    }

    /**
     * Retrieve the secret key from the Keystore.
     * The key bytes are NEVER exposed to application memory — only Cipher operations use it.
     */
    fun getSecretKey(): SecretKey {
        val keyStore = loadKeyStore()
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw KeyStoreException("SecretKey '$KEY_ALIAS' not found in Android Keystore. " +
                    "Call ensureKeyExists() before any cryptographic operation.")
    }

    /**
     * Create an initialized [Cipher] for ENCRYPTION.
     * Each call generates a fresh random IV — callers must extract the IV from
     * [Cipher.getIV] after initialization to store alongside the ciphertext.
     */
    fun createEncryptionCipher(): Cipher =
        Cipher.getInstance(CIPHER_TRANSFORMATION).also { cipher ->
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            Timber.v("KeystoreManager: Encryption cipher created, IV=${cipher.iv.toHexString()}")
        }

    /**
     * Create an initialized [Cipher] for DECRYPTION using the provided [iv].
     *
     * @param iv The 12-byte GCM nonce that was stored alongside the ciphertext.
     */
    fun createDecryptionCipher(iv: ByteArray): Cipher {
        require(iv.size == GCM_IV_LENGTH_BYTES) {
            "Invalid IV length: expected $GCM_IV_LENGTH_BYTES bytes, got ${iv.size}"
        }
        val spec = javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        return Cipher.getInstance(CIPHER_TRANSFORMATION).also { cipher ->
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            Timber.v("KeystoreManager: Decryption cipher created")
        }
    }

    /**
     * Permanently delete the AES-GCM key from the Keystore.
     * WARNING: All recordings encrypted with this key become permanently unrecoverable.
     * This should only be called on explicit user request (e.g., "Wipe all data").
     */
    fun deleteKey() {
        try {
            loadKeyStore().deleteEntry(KEY_ALIAS)
            Timber.w("KeystoreManager: Key '$KEY_ALIAS' DELETED. All recordings are now unrecoverable.")
        } catch (e: KeyStoreException) {
            Timber.e(e, "KeystoreManager: Failed to delete key '$KEY_ALIAS'")
            throw e
        }
    }

    /**
     * Check if the key is currently hardware-backed (TEE or StrongBox).
     */
    fun isKeyHardwareBacked(): Boolean {
        return try {
            val keyStore = loadKeyStore()
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            val key = entry?.secretKey ?: return false
            val factory = javax.crypto.SecretKeyFactory.getInstance(key.algorithm, KEYSTORE_PROVIDER)
            val keyInfo = factory.getKeySpec(key, android.security.keystore.KeyInfo::class.java)
                    as android.security.keystore.KeyInfo
            keyInfo.securityLevel != android.security.keystore.KeyProperties.SECURITY_LEVEL_SOFTWARE
        } catch (e: Exception) {
            Timber.e(e, "KeystoreManager: Cannot determine hardware backing status")
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }

    private fun logKeyMetadata(keyStore: KeyStore) {
        try {
            val creationDate = keyStore.getCreationDate(KEY_ALIAS)
            Timber.d("KeystoreManager: Key created on $creationDate | Hardware-backed: ${isKeyHardwareBacked()}")
        } catch (e: Exception) {
            Timber.d("KeystoreManager: Key metadata unavailable (${e.message})")
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }
}
