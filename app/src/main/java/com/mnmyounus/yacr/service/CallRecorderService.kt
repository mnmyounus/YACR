/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  service/CallRecorderService.kt          ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ║                                                                              ║
 * ║  Foreground service that orchestrates the full recording lifecycle:          ║
 * ║                                                                              ║
 * ║   1. Receives CallEvent (STARTED / ENDED) via broadcast                     ║
 * ║   2. Launches the AudioRecordingEngine on a dedicated IO coroutine          ║
 * ║   3. On call end: persists the encrypted .yacr file to the database         ║
 * ║   4. Manages a persistent "Recording Active" notification (API ≥ 26)        ║
 * ║   5. Resolves contact name from ContentResolver for caller ID               ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mnmyounus.yacr.R
import com.mnmyounus.yacr.data.local.datastore.YACRPreferences
import com.mnmyounus.yacr.domain.model.CallEvent
import com.mnmyounus.yacr.domain.model.CallType
import com.mnmyounus.yacr.domain.model.Recording
import com.mnmyounus.yacr.domain.repository.RecordingRepository
import com.mnmyounus.yacr.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class CallRecorderService : LifecycleService() {

    companion object {
        // ── Intent Actions ────────────────────────────────────────────────
        const val ACTION_START_RECORDING = "com.mnmyounus.yacr.START_RECORDING"
        const val ACTION_STOP_RECORDING  = "com.mnmyounus.yacr.STOP_RECORDING"
        const val ACTION_CALL_ENDED      = "com.mnmyounus.yacr.CALL_ENDED"

        // ── Intent Extras ─────────────────────────────────────────────────
        const val EXTRA_PHONE_NUMBER     = "extra_phone_number"
        const val EXTRA_CALLER_NAME      = "extra_caller_name"
        const val EXTRA_CALL_TYPE        = "extra_call_type"
        const val EXTRA_SOURCE_PACKAGE   = "extra_source_package"

        // ── Notification ID ───────────────────────────────────────────────
        private const val NOTIFICATION_ID_RECORDING = 1001

        // ── Recording Output Directory ────────────────────────────────────
        private const val RECORDINGS_DIR = "yacr_recordings"

        /** Start this service with a recording intent. */
        fun startRecording(
            context: Context,
            phoneNumber: String,
            callerName: String?,
            callType: CallType,
            sourcePackage: String? = null
        ) {
            val intent = Intent(context, CallRecorderService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_PHONE_NUMBER,   phoneNumber)
                putExtra(EXTRA_CALLER_NAME,    callerName ?: "")
                putExtra(EXTRA_CALL_TYPE,      callType.name)
                putExtra(EXTRA_SOURCE_PACKAGE, sourcePackage)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Signal an active recording to stop and save. */
        fun stopRecording(context: Context) {
            context.startService(Intent(context, CallRecorderService::class.java).apply {
                action = ACTION_STOP_RECORDING
            })
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Injected Dependencies
    // ─────────────────────────────────────────────────────────────────────────

    @Inject lateinit var audioEngine: AudioRecordingEngine
    @Inject lateinit var repository: RecordingRepository
    @Inject lateinit var preferences: YACRPreferences

    // ─────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────

    private var recordingJob: Job? = null
    private var currentCallStartMs: Long = 0L
    private var currentPhoneNumber: String = ""
    private var currentCallerName: String = ""
    private var currentCallType: CallType = CallType.CELLULAR
    private var currentSourcePackage: String? = null
    private var currentOutputFile: File? = null
    private var isRecording: Boolean = false

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Timber.d("CallRecorderService: Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_RECORDING -> handleStartRecording(intent)
            ACTION_STOP_RECORDING  -> handleStopRecording()
            ACTION_CALL_ENDED      -> handleStopRecording()
            else                   -> Timber.w("CallRecorderService: Unknown action: ${intent?.action}")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        Timber.d("CallRecorderService: onDestroy")
        if (isRecording) {
            Timber.w("CallRecorderService: Destroyed while recording — performing emergency save")
            handleStopRecording()
        }
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Recording Lifecycle Handlers
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleStartRecording(intent: Intent) {
        if (isRecording) {
            Timber.w("CallRecorderService: Already recording — ignoring duplicate start")
            return
        }

        val phoneNumber   = intent.getStringExtra(EXTRA_PHONE_NUMBER)   ?: "unknown"
        val callerName    = intent.getStringExtra(EXTRA_CALLER_NAME)    ?: ""
        val callTypeName  = intent.getStringExtra(EXTRA_CALL_TYPE)      ?: CallType.CELLULAR.name
        val sourcePackage = intent.getStringExtra(EXTRA_SOURCE_PACKAGE)

        currentPhoneNumber   = phoneNumber
        currentCallType      = runCatching { CallType.valueOf(callTypeName) }.getOrDefault(CallType.CELLULAR)
        currentSourcePackage = sourcePackage
        currentCallStartMs   = System.currentTimeMillis()

        // Resolve caller name (async, best-effort — recording starts immediately)
        lifecycleScope.launch {
            currentCallerName = callerName.ifBlank {
                withContext(Dispatchers.IO) {
                    resolveContactName(phoneNumber) ?: phoneNumber
                }
            }
        }

        // Create the output file
        val outputFile = createOutputFile(phoneNumber)
        currentOutputFile = outputFile

        // Start foreground notification immediately (required before any long-running work)
        startForeground(NOTIFICATION_ID_RECORDING, buildRecordingNotification(phoneNumber))

        isRecording = true

        // Launch capture coroutine
        recordingJob = lifecycleScope.launch {
            try {
                audioEngine.startRecording(outputFile)
            } catch (e: SecurityException) {
                Timber.e(e, "CallRecorderService: RECORD_AUDIO permission denied")
                handleEngineFailure("Microphone permission denied")
            } catch (e: Exception) {
                Timber.e(e, "CallRecorderService: Recording engine threw an exception")
                handleEngineFailure(e.message ?: "Unknown recording error")
            }
        }

        Timber.i(
            "CallRecorderService: Recording STARTED — " +
            "$currentCallType | $phoneNumber | ${outputFile.name}"
        )
    }

    private fun handleStopRecording() {
        if (!isRecording) {
            Timber.w("CallRecorderService: Stop called but not recording")
            stopSelf()
            return
        }

        Timber.d("CallRecorderService: Stop requested — finalizing recording")
        audioEngine.stop()
        recordingJob?.cancel()
        recordingJob = null
        isRecording = false

        // Persist the recording to database asynchronously
        lifecycleScope.launch {
            saveRecordingToDatabase()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleEngineFailure(reason: String) {
        Timber.e("CallRecorderService: Engine failure — $reason")
        isRecording = false
        recordingJob = null
        currentOutputFile?.let { file ->
            if (file.exists() && file.length() < 100L) {
                file.delete() // Remove empty/corrupt file
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Database Persistence
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun saveRecordingToDatabase() {
        val outputFile = currentOutputFile ?: run {
            Timber.e("CallRecorderService: No output file to save")
            return
        }

        if (!outputFile.exists() || outputFile.length() < 100L) {
            Timber.w("CallRecorderService: Output file empty or missing — not saving")
            outputFile.delete()
            return
        }

        val durationMs = System.currentTimeMillis() - currentCallStartMs

        val recording = Recording(
            id                = UUID.randomUUID().toString(),
            callerName        = currentCallerName.ifBlank { currentPhoneNumber },
            phoneNumber       = currentPhoneNumber,
            startTimestampMs  = currentCallStartMs,
            durationMs        = durationMs,
            encryptedFilePath = outputFile.absolutePath,
            fileSizeBytes     = outputFile.length(),
            callType          = currentCallType,
            sourcePackage     = currentSourcePackage
        )

        try {
            repository.saveRecording(recording)
            preferences.incrementTotalRecordingsCreated()
            Timber.i(
                "CallRecorderService: Saved recording — " +
                "${recording.callerName} | ${durationMs / 1000}s | ${outputFile.length() / 1024}KB encrypted"
            )
        } catch (e: Exception) {
            Timber.e(e, "CallRecorderService: Failed to save recording to database")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File Management
    // ─────────────────────────────────────────────────────────────────────────

    private fun createOutputFile(phoneNumber: String): File {
        val recordingsDir = File(filesDir, RECORDINGS_DIR).apply { mkdirs() }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sanitizedNumber = phoneNumber.replace(Regex("[^0-9a-zA-Z_\\-+]"), "_")
            .take(30) // Truncate to avoid excessively long filenames

        val filename = "YACR_${timestamp}_${sanitizedNumber}.yacr"
        return File(recordingsDir, filename)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Contact Resolution
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Query ContentResolver to look up a contact name by phone number.
     * Returns null if the number is not found or permission is denied.
     */
    private fun resolveContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val cursor: Cursor? = contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: SecurityException) {
            Timber.w("CallRecorderService: READ_CONTACTS not granted — skipping name lookup")
            null
        } catch (e: Exception) {
            Timber.e(e, "CallRecorderService: Contact resolution failed for $phoneNumber")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildRecordingNotification(callerInfo: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CallRecorderService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(
            this,
            getString(R.string.notification_channel_recording_id)
        )
            .setSmallIcon(R.drawable.ic_recording_indicator)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText("$callerInfo — ${formatCallType(currentCallType)}")
            .setContentIntent(pendingOpenApp)
            .addAction(
                R.drawable.ic_stop,
                getString(R.string.notification_recording_stop),
                pendingStop
            )
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)  // Hidden on lock screen
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun formatCallType(type: CallType): String = when (type) {
        CallType.CELLULAR     -> "Cellular"
        CallType.WHATSAPP     -> "WhatsApp"
        CallType.SIGNAL       -> "Signal"
        CallType.TELEGRAM     -> "Telegram"
        CallType.VIBER        -> "Viber"
        CallType.MESSENGER    -> "Messenger"
        CallType.SKYPE        -> "Skype"
        CallType.GOOGLE_MEET  -> "Google Meet"
        CallType.ZOOM         -> "Zoom"
        CallType.VOIP_OTHER   -> "VoIP"
    }
}
