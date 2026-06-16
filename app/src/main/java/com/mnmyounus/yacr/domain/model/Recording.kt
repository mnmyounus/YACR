/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  domain/model/Recording.kt               ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Pure domain model for a call recording.
 * Has NO dependency on any framework (Room, Gson, etc.).
 * Parcelable for navigation argument passing.
 */
@Parcelize
data class Recording(
    /** Unique identifier (UUID). */
    val id: String,
    /** Contact name or phone number. "Unknown" if unresolvable. */
    val callerName: String,
    /** Raw phone number or VoIP URI. */
    val phoneNumber: String,
    /** Unix epoch timestamp (ms) when the call started. */
    val startTimestampMs: Long,
    /** Duration of the call in milliseconds. */
    val durationMs: Long,
    /** Absolute path to the encrypted audio file on disk. */
    val encryptedFilePath: String,
    /** File size in bytes (encrypted). */
    val fileSizeBytes: Long,
    /** Whether this was a VoIP or cellular call. */
    val callType: CallType,
    /** The source package (e.g. com.whatsapp) — null for cellular. */
    val sourcePackage: String?,
    /** Whether this recording has been flagged/starred. */
    val isFlagged: Boolean = false
) : Parcelable

/**
 * Discriminates the origin of the call recording.
 */
enum class CallType {
    /** Standard cellular PSTN call. */
    CELLULAR,
    /** WhatsApp voice/video call. */
    WHATSAPP,
    /** Signal private call. */
    SIGNAL,
    /** Telegram voice call. */
    TELEGRAM,
    /** Viber call. */
    VIBER,
    /** Facebook Messenger call. */
    MESSENGER,
    /** Skype call. */
    SKYPE,
    /** Google Meet call. */
    GOOGLE_MEET,
    /** Zoom call. */
    ZOOM,
    /** Any other VoIP/SIP application. */
    VOIP_OTHER;

    val isCellular: Boolean get() = this == CELLULAR
    val isVoip: Boolean get() = !isCellular

    companion object {
        fun fromPackage(packageName: String?): CallType = when (packageName) {
            "com.whatsapp", "com.whatsapp.w4b"               -> WHATSAPP
            "org.thoughtcrime.securesms"                      -> SIGNAL
            "org.telegram.messenger",
            "org.telegram.messenger.web"                      -> TELEGRAM
            "com.viber.voip"                                  -> VIBER
            "com.facebook.orca"                               -> MESSENGER
            "com.skype.raider", "com.microsoft.teams"         -> SKYPE
            "com.google.android.apps.meetings"                -> GOOGLE_MEET
            "us.zoom.videomeetings"                           -> ZOOM
            null                                              -> CELLULAR
            else                                              -> VOIP_OTHER
        }
    }
}
