package com.scamshield.android.service

import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScreenedCallState {
    data object Idle : ScreenedCallState
    data object TrustedCall : ScreenedCallState
    data class UnknownCall(
        val canonicalNumber: String?,
        val screenedAtMillis: Long,
        val offHookConnected: Boolean = false
    ) : ScreenedCallState
}

sealed interface CallConnectAction {
    data object NoAction : CallConnectAction
    data object SkipTrustedContact : CallConnectAction
    data class StartUnknownCall(val canonicalNumber: String?) : CallConnectAction
}

/**
 * Coordinates incoming CallScreeningService classification with TelephonyManager OFFHOOK/IDLE
 * transitions while ensuring trusted contacts are strictly excluded from automatic analysis.
 */
@Singleton
class CallSessionTracker @Inject constructor() {

    companion object {
        private const val SCREENING_TTL_MILLIS = 120_000L
    }

    @Volatile
    private var currentState: ScreenedCallState = ScreenedCallState.Idle

    fun getCurrentState(): ScreenedCallState = currentState

    @Synchronized
    fun onIncomingCallScreened(
        decision: CallScreeningDecision,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        currentState = when (decision) {
            is CallScreeningDecision.TrustedContactBypassed -> ScreenedCallState.TrustedCall
            is CallScreeningDecision.UnknownCallerScreened -> ScreenedCallState.UnknownCall(
                canonicalNumber = decision.canonicalNumber,
                screenedAtMillis = nowMillis,
                offHookConnected = false
            )
            CallScreeningDecision.ScreeningDisabled -> ScreenedCallState.Idle
        }
    }

    @Synchronized
    fun onPhoneOffHook(
        autoAnalyzeEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): CallConnectAction {
        if (!autoAnalyzeEnabled) return CallConnectAction.NoAction

        return when (val state = currentState) {
            ScreenedCallState.Idle -> CallConnectAction.NoAction
            ScreenedCallState.TrustedCall -> CallConnectAction.SkipTrustedContact
            is ScreenedCallState.UnknownCall -> {
                if (nowMillis - state.screenedAtMillis > SCREENING_TTL_MILLIS) {
                    currentState = ScreenedCallState.Idle
                    CallConnectAction.NoAction
                } else if (!state.offHookConnected) {
                    currentState = state.copy(offHookConnected = true)
                    CallConnectAction.StartUnknownCall(state.canonicalNumber)
                } else {
                    // Already transitioned OFFHOOK for this call
                    CallConnectAction.NoAction
                }
            }
        }
    }

    @Synchronized
    fun onPhoneIdle() {
        currentState = ScreenedCallState.Idle
    }
}

