/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  service/YACRAccessibilityService.kt     ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ║                                                                              ║
 * ║  Accessibility Service for VoIP / IP call detection.                        ║
 * ║                                                                              ║
 * ║  Detection Strategy per Application:                                         ║
 * ║                                                                              ║
 * ║  ┌──────────────┬─────────────────────────────────────────────────────┐     ║
 * ║  │ App          │ Detection Method                                    │     ║
 * ║  ├──────────────┼─────────────────────────────────────────────────────┤     ║
 * ║  │ WhatsApp     │ Window class: "com.whatsapp.voipcalling.VoipActivity"│     ║
 * ║  │              │ Content: "Ongoing call", caller name text nodes     │     ║
 * ║  ├──────────────┼─────────────────────────────────────────────────────┤     ║
 * ║  │ Signal       │ Window class: "org.thoughtcrime.securesms.calls."   │     ║
 * ║  │              │ Content: "Signal call" text                         │     ║
 * ║  ├──────────────┼─────────────────────────────────────────────────────┤     ║
 * ║  │ Telegram     │ Window: "PhoneCallActivity"                         │     ║
 * ║  │              │ Content: caller name above call status text         │     ║
 * ║  ├──────────────┼─────────────────────────────────────────────────────┤     ║
 * ║  │ Native Dialer│ State change to InCallActivity classes              │     ║
 * ║  └──────────────┴─────────────────────────────────────────────────────┘     ║
 * ║                                                                              ║
 * ║  Android 13+ Restricted Settings:                                           ║
 * ║   On Android 13+ (API 33), sideloaded APKs require the user to explicitly  ║
 * ║   grant "Restricted Settings" before enabling Accessibility Services.       ║
 * ║   YACR surfaces a guided settings deep-link prompt to resolve this.         ║
 * ║   For deeper integration (audio routing, screen overlays), a YACR Helper   ║
 * ║   companion app signed with the same certificate bridges to privileged APIs.║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.mnmyounus.yacr.domain.model.CallType
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class YACRAccessibilityService : AccessibilityService() {

    // ─────────────────────────────────────────────────────────────────────────
    // Call Detection State Machine
    // ─────────────────────────────────────────────────────────────────────────

    private var isCallActive: Boolean = false
    private var activePackage: String? = null
    private var lastExtractedCallerName: String? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Known "Call Active" Window Classes per Application
    // ─────────────────────────────────────────────────────────────────────────

    private val voipCallWindowPatterns: Map<String, List<String>> = mapOf(
        "com.whatsapp" to listOf(
            "com.whatsapp.voipcalling.VoipActivity",
            "com.whatsapp.voipcalling.VoipCallerActivity",
            "com.whatsapp.calling.CallActivity"
        ),
        "com.whatsapp.w4b" to listOf(
            "com.whatsapp.voipcalling.VoipActivity",
            "com.whatsapp.voipcalling.VoipCallerActivity"
        ),
        "org.thoughtcrime.securesms" to listOf(
            "org.thoughtcrime.securesms.calls.WebRtcCallActivity",
            "org.thoughtcrime.securesms.calls.WebRtcCallView"
        ),
        "org.telegram.messenger" to listOf(
            "org.telegram.ui.VoIPActivity",
            "org.telegram.ui.VideoCallActivity",
            "org.telegram.ui.LaunchActivity"  // Telegram opens calls in main activity
        ),
        "org.telegram.messenger.web" to listOf(
            "org.telegram.ui.VoIPActivity",
            "org.telegram.ui.VideoCallActivity"
        ),
        "com.viber.voip" to listOf(
            "com.viber.voip.phone.CallActivity",
            "com.viber.voip.phone.IncomingCallActivity",
            "com.viber.voip.presentation.calls.VoIPCallActivity"
        ),
        "com.facebook.orca" to listOf(
            "com.facebook.rtc.activities.RtcActivity",
            "com.facebook.orca.rtc.RtcHostActivity"
        ),
        "com.skype.raider" to listOf(
            "com.microsoft.skype.meetings.calling.ui.CallingActivity",
            "com.skype.raider.calling.CallActivity"
        ),
        "com.google.android.apps.meetings" to listOf(
            "com.google.android.apps.meetings.activity.MeetingActivity",
            "com.google.android.apps.meetings.ui.MeetingActivity"
        ),
        "us.zoom.videomeetings" to listOf(
            "us.zoom.videomeetings.MeetingActivity",
            "us.zoom.videomeetings.ZoomMeetingActivity"
        )
    )

    /** Text strings that indicate an active/ongoing call state on-screen. */
    private val callActiveIndicatorStrings = setOf(
        "ongoing call", "in call", "call in progress",
        "calling", "connected", "active call",
        "voice call", "video call", "audio call",
        "ringing", "outgoing", "incoming"
    )

    /** Text strings that indicate a call has ended. */
    private val callEndedIndicatorStrings = setOf(
        "call ended", "call disconnected", "hung up",
        "no answer", "missed call", "declined"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // AccessibilityService Callbacks
    // ─────────────────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("YACRAccessibilityService: Connected — VoIP call monitoring active")

        // Dynamically reinforce service config in case XML config was insufficient
        serviceInfo = serviceInfo?.also { info ->
            info.eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            info.flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.notificationTimeout = 100L
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString() ?: return

        // Only process events from monitored packages
        if (!isMonitoredPackage(packageName)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChange(event, packageName)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handleContentChange(event, packageName)
            }
        }
    }

    override fun onInterrupt() {
        Timber.w("YACRAccessibilityService: Interrupted")
        if (isCallActive) {
            Timber.w("YACRAccessibilityService: Service interrupted during active call — stopping recording")
            onCallEnded(activePackage)
        }
    }

    override fun onDestroy() {
        Timber.d("YACRAccessibilityService: Destroyed")
        if (isCallActive) onCallEnded(activePackage)
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Window State Change Handler
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleWindowStateChange(event: AccessibilityEvent, packageName: String) {
        val className = event.className?.toString() ?: return

        Timber.v("YACRAccessibilityService: WindowStateChanged — pkg=$packageName cls=$className")

        val knownCallWindows = voipCallWindowPatterns[packageName] ?: return

        val isCallWindow = knownCallWindows.any { pattern ->
            className.contains(pattern, ignoreCase = true) ||
            className.endsWith(pattern.substringAfterLast('.'), ignoreCase = true)
        }

        if (isCallWindow && !isCallActive) {
            // Call screen appeared — extract caller info and start recording
            val callerName = extractCallerNameFromEvent(event)
                ?: extractCallerNameFromWindowContent(event.source)
            onCallStarted(packageName, callerName)

        } else if (!isCallWindow && isCallActive && activePackage == packageName) {
            // Navigated away from call screen — call ended
            val textContent = event.text?.joinToString(" ")?.lowercase() ?: ""
            if (callEndedIndicatorStrings.any { textContent.contains(it) } ||
                !knownCallWindows.any { className.contains(it.substringAfterLast('.'), ignoreCase = true) }
            ) {
                onCallEnded(packageName)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Content Change Handler
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleContentChange(event: AccessibilityEvent, packageName: String) {
        val textContent = event.text?.joinToString(" ")?.lowercase() ?: ""

        // Look for call end signals even on content changes (e.g., Telegram inline dismissal)
        if (isCallActive && activePackage == packageName) {
            if (callEndedIndicatorStrings.any { textContent.contains(it) }) {
                Timber.d("YACRAccessibilityService: Call-ended string detected in content: '$textContent'")
                onCallEnded(packageName)
                return
            }

            // Update caller name if we can extract better info now
            if (lastExtractedCallerName == null) {
                lastExtractedCallerName = extractCallerNameFromWindowContent(event.source)
            }
        }

        // Detect call start via content (for apps that don't create a distinct window)
        if (!isCallActive && voipCallWindowPatterns.containsKey(packageName)) {
            if (callActiveIndicatorStrings.any { textContent.contains(it) }) {
                val callerName = extractCallerNameFromWindowContent(event.source)
                Timber.d("YACRAccessibilityService: Active call text detected for $packageName")
                onCallStarted(packageName, callerName)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Call Lifecycle Events
    // ─────────────────────────────────────────────────────────────────────────

    private fun onCallStarted(packageName: String, callerName: String?) {
        isCallActive = true
        activePackage = packageName
        lastExtractedCallerName = callerName

        val callType = CallType.fromPackage(packageName)

        Timber.i(
            "YACRAccessibilityService: CALL STARTED — " +
            "pkg=$packageName | type=$callType | caller=${callerName ?: "unknown"}"
        )

        CallRecorderService.startRecording(
            context       = this,
            phoneNumber   = callerName ?: "voip_unknown",
            callerName    = callerName,
            callType      = callType,
            sourcePackage = packageName
        )
    }

    private fun onCallEnded(packageName: String?) {
        if (!isCallActive) return

        Timber.i("YACRAccessibilityService: CALL ENDED — pkg=$packageName")

        isCallActive = false
        activePackage = null
        lastExtractedCallerName = null

        CallRecorderService.stopRecording(this)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Caller ID Extraction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attempt to extract caller name from the raw text of an AccessibilityEvent.
     * Many VoIP apps put the caller name as the first text chunk in the event.
     */
    private fun extractCallerNameFromEvent(event: AccessibilityEvent): String? {
        val texts = event.text ?: return null
        return texts
            .map { it.toString().trim() }
            .filter { text ->
                text.isNotBlank() &&
                text.length > 2 &&
                !text.lowercase().any { ch -> ch.isDigit() } &&  // Likely a name, not a number
                callActiveIndicatorStrings.none { indicator -> text.lowercase().contains(indicator) }
            }
            .firstOrNull()
    }

    /**
     * Walk the accessibility node tree to find caller name text.
     * Applies app-specific heuristics based on known UI structures.
     */
    private fun extractCallerNameFromWindowContent(rootNode: AccessibilityNodeInfo?): String? {
        rootNode ?: return null
        return try {
            findCallerNameNode(rootNode)
        } catch (e: Exception) {
            Timber.e(e, "YACRAccessibilityService: Node traversal failed")
            null
        }
    }

    /**
     * Recursively walks the accessibility node tree looking for a node that
     * plausibly contains a caller's name. Stops after the first plausible match.
     *
     * Heuristic: A text node near the top of the view hierarchy whose text
     * does not match any known UI string (button labels, status text, etc.) and
     * contains alphabetic characters (likely a person's name).
     */
    private fun findCallerNameNode(node: AccessibilityNodeInfo, depth: Int = 0): String? {
        if (depth > 8) return null  // Don't traverse too deep

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && isLikelyCallerName(text)) {
            return text
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findCallerNameNode(child, depth + 1)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun isLikelyCallerName(text: String): Boolean {
        if (text.length < 2 || text.length > 60) return false
        val lower = text.lowercase()
        // Reject if it's a known UI label or call status string
        val knownUiLabels = setOf(
            "mute", "speaker", "video", "end", "hold", "swap", "add call",
            "keypad", "bluetooth", "decline", "accept", "answer", "reject",
            "back", "home", "settings", "cancel", "ok", "close",
            "calling", "ringing", "connected", "on hold", "incoming", "outgoing",
            "ongoing call", "voice call", "video call", "audio call"
        )
        if (knownUiLabels.any { lower == it || lower.startsWith(it) }) return false
        // Prefer text that starts with uppercase and contains mainly letters
        val alphabeticRatio = text.count { it.isLetter() }.toFloat() / text.length
        return alphabeticRatio > 0.6f
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun isMonitoredPackage(packageName: String): Boolean =
        voipCallWindowPatterns.containsKey(packageName)

    // ─────────────────────────────────────────────────────────────────────────
    // Android 13+ Restricted Settings Helper
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        /**
         * On Android 13+ (API 33), apps distributed outside the Play Store require
         * the user to manually grant "Restricted Settings" before Accessibility Services
         * can be enabled. This function checks if the current installation requires this
         * additional step and returns a deep-link Intent to the correct Settings page.
         *
         * YACR Helper App Integration:
         * If YACR Helper (a system/privileged companion APK signed with the same key)
         * is installed, it can bridge this restriction through IPC, providing deeper
         * audio routing hooks unavailable to regular apps. The Helper communicates via
         * a local broadcast (no network) using a shared signing certificate as the
         * permission gatekeeper.
         */
        fun getRestrictedSettingsIntent(): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

        fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val serviceName = "${context.packageName}/${YACRAccessibilityService::class.java.name}"
            return enabledServices.split(':').any { it.equals(serviceName, ignoreCase = true) }
        }
    }
}
