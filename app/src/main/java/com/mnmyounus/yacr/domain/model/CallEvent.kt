/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  domain/model/CallEvent.kt               ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.domain.model

/**
 * Sealed class representing events emitted by call detection sources
 * (TelephonyManager, AccessibilityService) and consumed by CallRecorderService.
 */
sealed class CallEvent {

    /**
     * A call has become active (answered / connected).
     *
     * @param phoneNumber  The remote party's number or VoIP identifier.
     * @param callerName   Resolved contact name, or null if unknown.
     * @param callType     Origin of the call.
     * @param sourcePackage Package name for VoIP calls; null for cellular.
     */
    data class CallStarted(
        val phoneNumber: String,
        val callerName: String?,
        val callType: CallType,
        val sourcePackage: String? = null
    ) : CallEvent()

    /**
     * A call has ended (hung up, rejected, or call dropped).
     */
    object CallEnded : CallEvent()

    /**
     * An incoming call is ringing but not yet answered.
     * Used for logging; recording does not start until [CallStarted].
     */
    data class CallRinging(
        val phoneNumber: String,
        val callerName: String?
    ) : CallEvent()

    /**
     * An error condition was detected in the call detection pipeline.
     */
    data class DetectionError(val reason: String, val cause: Throwable? = null) : CallEvent()
}
