package com.scamshield.android

import com.google.common.truth.Truth.assertThat
import com.scamshield.android.audio.AudioMonitorController
import com.scamshield.android.domain.model.CallerTrustStatus
import com.scamshield.android.service.CallConnectAction
import com.scamshield.android.service.CallScreeningDecision
import com.scamshield.android.service.CallSessionTracker
import com.scamshield.android.service.ScreenedCallState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CallLifecycleAndSessionTest {

    @Test
    fun `trusted caller never starts live analysis when call connects`() {
        val tracker = CallSessionTracker()
        tracker.onIncomingCallScreened(CallScreeningDecision.TrustedContactBypassed("Dad"), nowMillis = 1_000L)

        val action = tracker.onPhoneOffHook(autoAnalyzeEnabled = true, nowMillis = 3_000L)
        assertThat(action).isEqualTo(CallConnectAction.SkipTrustedContact)
    }

    @Test
    fun `unknown caller starts live analysis on connect and prevents duplicate trigger`() {
        val tracker = CallSessionTracker()
        tracker.onIncomingCallScreened(
            CallScreeningDecision.UnknownCallerScreened(
                trustStatus = CallerTrustStatus.UNKNOWN,
                canonicalNumber = "+919876543210",
                contactsPermissionGranted = true,
                priorRiskLevel = null
            ),
            nowMillis = 10_000L
        )

        val firstConnect = tracker.onPhoneOffHook(autoAnalyzeEnabled = true, nowMillis = 15_000L)
        val repeatOffHook = tracker.onPhoneOffHook(autoAnalyzeEnabled = true, nowMillis = 16_000L)

        assertThat(firstConnect).isEqualTo(CallConnectAction.StartUnknownCall("+919876543210"))
        assertThat(repeatOffHook).isEqualTo(CallConnectAction.NoAction)

        tracker.onPhoneIdle()
        assertThat(tracker.getCurrentState()).isEqualTo(ScreenedCallState.Idle)
    }

    @Test
    fun `unscreened call or expired screening does not auto start live recording`() {
        val tracker = CallSessionTracker()
        assertThat(tracker.onPhoneOffHook(autoAnalyzeEnabled = true, nowMillis = 1_000L))
            .isEqualTo(CallConnectAction.NoAction)

        tracker.onIncomingCallScreened(
            CallScreeningDecision.UnknownCallerScreened(
                trustStatus = CallerTrustStatus.UNKNOWN,
                canonicalNumber = "+919900001111",
                contactsPermissionGranted = true,
                priorRiskLevel = null
            ),
            nowMillis = 1_000L
        )
        // More than 2 minutes elapsed before OFFHOOK
        assertThat(tracker.onPhoneOffHook(autoAnalyzeEnabled = true, nowMillis = 200_000L))
            .isEqualTo(CallConnectAction.NoAction)
    }

    @Test
    fun `audio monitor controller stop signal emits immediately on call disconnect`() = runTest {
        val controller = AudioMonitorController()
        val deferred = async { controller.stopSignal.first() }
        testScheduler.advanceUntilIdle()
        controller.requestStopMonitoring()
        deferred.await()
    }
}

