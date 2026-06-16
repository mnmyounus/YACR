/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder                                               ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ║  File      : YACRApplication.kt                                              ║
 * ║                                                                              ║
 * ║  Application entry point.                                                    ║
 * ║  Responsibilities:                                                           ║
 * ║   1. Initialize Hilt dependency injection graph                              ║
 * ║   2. Plant Timber logging trees (verbose in debug, silent in release)        ║
 * ║   3. Create notification channels on API 26+                                 ║
 * ║   4. Validate Android Keystore integrity at startup                          ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.mnmyounus.yacr.data.crypto.KeystoreManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class YACRApplication : Application() {

    @Inject
    lateinit var keystoreManager: KeystoreManager

    override fun onCreate() {
        super.onCreate()
        initLogging()
        createNotificationChannels()
        validateKeystore()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logging
    // ─────────────────────────────────────────────────────────────────────────

    private fun initLogging() {
        if (BuildConfig.ENABLE_VERBOSE_LOGGING) {
            Timber.plant(
                object : Timber.DebugTree() {
                    override fun createStackElementTag(element: StackTraceElement): String =
                        "YACR/${super.createStackElementTag(element)}"
                }
            )
            Timber.d("YACR ${BuildConfig.APP_VERSION} starting — Developer: ${BuildConfig.DEVELOPER}")
        } else {
            // Release build: plant a silent tree so Timber calls are no-ops
            Timber.plant(SilentTree())
        }
    }

    /**
     * Silent Timber tree for release builds.
     * Ensures zero log leakage in production without changing call-sites.
     */
    private class SilentTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) = Unit
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification Channels
    // ─────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = getSystemService<NotificationManager>()
            ?: run {
                Timber.e("NotificationManager unavailable — cannot create channels")
                return
            }

        val channels = listOf(
            NotificationChannel(
                getString(R.string.notification_channel_recording_id),
                getString(R.string.notification_channel_recording_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description     = getString(R.string.notification_channel_recording_desc)
                enableLights(true)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            },
            NotificationChannel(
                getString(R.string.notification_channel_alerts_id),
                getString(R.string.notification_channel_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_alerts_desc)
                setShowBadge(true)
            }
        )

        notificationManager.createNotificationChannels(channels)
        Timber.d("Notification channels registered: ${channels.map { it.id }}")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Keystore Validation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pre-warm the Keystore at startup to detect hardware-backed key issues
     * early rather than failing mid-call during a recording session.
     */
    private fun validateKeystore() {
        try {
            keystoreManager.ensureKeyExists()
            Timber.d("Android Keystore validated — AES-GCM-256 key is present and hardware-backed")
        } catch (e: Exception) {
            Timber.e(e, "CRITICAL: Keystore validation failed at startup")
            // The encryption pipeline will surface this error to the user
            // before any recording attempt begins.
        }
    }
}
