package com.scamshield.android.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Android Telecom CallScreeningService implementation.
 * - Matches incoming caller numbers against local Contacts on-device.
 * - Excludes saved trusted contacts from any monitoring, storage, or scam alerts.
 * - Never blocks, rejects, or silences incoming calls by default so legitimate calls are never dropped.
 */
@AndroidEntryPoint
class ScamCallScreeningService : CallScreeningService() {

    @Inject
    lateinit var callScreeningProcessor: CallScreeningProcessor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        // Screen only incoming calls on API 29+ (all calls on earlier versions are allowed promptly)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            respondToCall(callDetails, allowCallResponse())
            return
        }

        val presentation = callDetails.handlePresentation
        val isRestrictedOrUnknown = presentation == TelecomManager.PRESENTATION_RESTRICTED ||
            presentation == TelecomManager.PRESENTATION_UNKNOWN ||
            presentation == TelecomManager.PRESENTATION_PAYPHONE

        val rawNumber = callDetails.handle?.schemeSpecificPart

        serviceScope.launch {
            try {
                // Respect Android's 5-second CallScreeningService response window strictly.
                withTimeoutOrNull(3_500L) {
                    callScreeningProcessor.screenIncomingCall(
                        rawNumber = rawNumber,
                        isRestrictedOrPrivatePresentation = isRestrictedOrUnknown
                    )
                }
            } finally {
                respondToCall(callDetails, allowCallResponse())
            }
        }
    }

    private fun allowCallResponse(): CallResponse = CallResponse.Builder()
        .setDisallowCall(false)
        .setRejectCall(false)
        .setSkipCallLog(false)
        .setSkipNotification(false)
        .build()

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
