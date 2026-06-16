/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  service/BootReceiver.kt                 ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Handles system boot events to ensure YACR's monitoring infrastructure
 * is initialized after device restart.
 *
 * Note: The PhoneStateReceiver is statically declared in the manifest and
 * will be auto-registered by the system after boot. This receiver serves
 * as a hook for any boot-time initialization logic (e.g., cleaning up
 * any incomplete recordings from before a sudden shutdown).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Timber.d("BootReceiver: ${intent.action} received — YACR monitoring is active")
                // Future: Clean up any .yacr files that were left incomplete
                // during an unexpected shutdown (file header written but no chunks).
                cleanupIncompleteRecordings(context)
            }
        }
    }

    /**
     * Scan the recordings directory for files that are too small to be valid
     * recordings (header-only files from interrupted sessions) and remove them.
     */
    private fun cleanupIncompleteRecordings(context: Context) {
        try {
            val recordingsDir = java.io.File(context.filesDir, "yacr_recordings")
            if (!recordingsDir.exists()) return

            val incompleteFiles = recordingsDir.listFiles { file ->
                file.extension == "yacr" &&
                file.length() <= com.mnmyounus.yacr.data.crypto.EncryptionPipeline.HEADER_SIZE_BYTES
            } ?: return

            incompleteFiles.forEach { file ->
                Timber.w("BootReceiver: Removing incomplete recording: ${file.name}")
                file.delete()
            }

            if (incompleteFiles.isNotEmpty()) {
                Timber.i("BootReceiver: Cleaned up ${incompleteFiles.size} incomplete recording(s)")
            }
        } catch (e: Exception) {
            Timber.e(e, "BootReceiver: Cleanup failed")
        }
    }
}
