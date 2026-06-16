/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  service/PhoneStateReceiver.kt           ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ║                                                                              ║
 * ║  BroadcastReceiver for cellular PSTN call events.                           ║
 * ║                                                                              ║
 * ║  Handles:                                                                    ║
 * ║   • android.intent.action.PHONE_STATE   — incoming/idle state changes       ║
 * ║   • android.intent.action.NEW_OUTGOING_CALL — outgoing call dial events     ║
 * ║                                                                              ║
 * ║  State Machine:                                                              ║
 * ║   IDLE ──▶ RINGING ──▶ OFFHOOK (start recording) ──▶ IDLE (stop recording) ║
 * ║   IDLE ──▶ OFFHOOK (outgoing) ──▶ IDLE (stop recording)                    ║
 * ║                                                                              ║
 * ║  Android 9+ TelephonyManager.CALL_STATE_RINGING / OFFHOOK / IDLE           ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.mnmyounus.yacr.domain.model.CallType
import timber.log.Timber

class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        /** Persisted across onReceive calls via companion state — receiver is re-instantiated per broadcast. */
        @Volatile private var lastState: Int = TelephonyManager.CALL_STATE_IDLE
        @Volatile private var lastIncomingNumber: String = ""
        @Volatile private var callStartTimeMs: Long = 0L
        @Volatile private var isRecording: Boolean = false
        @Volatile private var savedOutgoingNumber: String = ""
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_NEW_OUTGOING_CALL -> handleOutgoingCall(context, intent)
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> handlePhoneState(context, intent)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Outgoing Call Handler
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleOutgoingCall(context: Context, intent: Intent) {
        val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: return
        savedOutgoingNumber = number
        Timber.d("PhoneStateReceiver: Outgoing call to $number")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phone State Handler
    // ─────────────────────────────────────────────────────────────────────────

    private fun handlePhoneState(context: Context, intent: Intent) {
        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        val state = when (stateStr) {
            TelephonyManager.EXTRA_STATE_IDLE    -> TelephonyManager.CALL_STATE_IDLE
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            else -> {
                Timber.w("PhoneStateReceiver: Unknown state string: $stateStr")
                return
            }
        }

        Timber.d(
            "PhoneStateReceiver: State ${stateToString(lastState)} → ${stateToString(state)} " +
            "| number='$incomingNumber' | outgoing='$savedOutgoingNumber'"
        )

        when {
            // ── Incoming call ringing ───────────────────────────────────────
            state == TelephonyManager.CALL_STATE_RINGING -> {
                lastIncomingNumber = incomingNumber
                Timber.d("PhoneStateReceiver: Incoming call ringing from $incomingNumber")
            }

            // ── Call answered (offhook) — start recording ───────────────────
            state == TelephonyManager.CALL_STATE_OFFHOOK &&
            lastState != TelephonyManager.CALL_STATE_OFFHOOK -> {
                val phoneNumber = when {
                    lastIncomingNumber.isNotBlank() -> lastIncomingNumber
                    savedOutgoingNumber.isNotBlank() -> savedOutgoingNumber
                    else -> "unknown"
                }
                callStartTimeMs = System.currentTimeMillis()
                isRecording = true

                Timber.i("PhoneStateReceiver: Call ANSWERED — starting recording for $phoneNumber")

                CallRecorderService.startRecording(
                    context       = context,
                    phoneNumber   = phoneNumber,
                    callerName    = null,  // Service resolves from Contacts
                    callType      = CallType.CELLULAR,
                    sourcePackage = null
                )
            }

            // ── Call ended (back to idle) — stop recording ──────────────────
            state == TelephonyManager.CALL_STATE_IDLE &&
            lastState == TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (isRecording) {
                    val durationMs = System.currentTimeMillis() - callStartTimeMs
                    Timber.i(
                        "PhoneStateReceiver: Call ENDED after ${durationMs / 1000}s — stopping recording"
                    )
                    CallRecorderService.stopRecording(context)
                    isRecording = false
                }
                // Reset tracking state
                lastIncomingNumber = ""
                savedOutgoingNumber = ""
                callStartTimeMs = 0L
            }

            // ── Missed or rejected call (ringing → idle without offhook) ────
            state == TelephonyManager.CALL_STATE_IDLE &&
            lastState == TelephonyManager.CALL_STATE_RINGING -> {
                Timber.d("PhoneStateReceiver: Call missed/rejected from $lastIncomingNumber")
                lastIncomingNumber = ""
            }
        }

        lastState = state
    }

    private fun stateToString(state: Int): String = when (state) {
        TelephonyManager.CALL_STATE_IDLE    -> "IDLE"
        TelephonyManager.CALL_STATE_RINGING -> "RINGING"
        TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
        else                                 -> "UNKNOWN($state)"
    }
}
