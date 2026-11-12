package com.scamshield.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.scamshield.android.audio.AudioMonitorController
import com.scamshield.android.data.preferences.AppPreferencesRepository
import com.scamshield.android.notifications.ScamNotificationManager
import com.scamshield.android.util.RoleAndPermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Listens for Android call state changes (OFFHOOK / IDLE) when READ_PHONE_STATE is granted:
 * - On OFFHOOK (call connected) for a screened UNKNOWN caller: attempts automatic start of
 *   LiveAudioAnalysisService, or shows an interactive one-tap prompt if Android background mic
 *   restrictions prevent silent foreground-service startup.
 * - Trusted contacts are strictly bypassed and never auto-recorded.
 * - On IDLE (call disconnected): stops active live analysis immediately and triggers recording save.
 */
@AndroidEntryPoint
class CallLifecycleReceiver : BroadcastReceiver() {

    @Inject lateinit var callSessionTracker: CallSessionTracker
    @Inject lateinit var preferencesRepository: AppPreferencesRepository
    @Inject lateinit var audioMonitorController: AudioMonitorController
    @Inject lateinit var notificationManager: ScamNotificationManager

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateExtra = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        when (stateExtra) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> handleCallConnected(context)
            TelephonyManager.EXTRA_STATE_IDLE -> handleCallDisconnected()
        }
    }

    private fun handleCallConnected(context: Context) {
        val action = callSessionTracker.onPhoneOffHook(
            autoAnalyzeEnabled = preferencesRepository.isAutoAnalyzeOnCallConnectSync()
        )
        if (action !is CallConnectAction.StartUnknownCall) return

        if (!RoleAndPermissionHelper.isMicrophoneGranted(context)) {
            notificationManager.notifyCallConnectedStartPrompt(action.canonicalNumber)
            return
        }

        val startIntent = LiveAudioAnalysisService.startIntent(
            context = context,
            phoneNumber = action.canonicalNumber
        )
        try {
            ContextCompat.startForegroundService(context, startIntent)
        } catch (_: Exception) {
            // Android 14+ blocks background microphone FGS start when no foreground exemption is active
            notificationManager.notifyCallConnectedStartPrompt(action.canonicalNumber)
        }
    }

    private fun handleCallDisconnected() {
        callSessionTracker.onPhoneIdle()
        notificationManager.cancelCallConnectedPrompt()
        if (audioMonitorController.state.value.isMonitoring) {
            audioMonitorController.requestStopMonitoring()
        }
    }
}

