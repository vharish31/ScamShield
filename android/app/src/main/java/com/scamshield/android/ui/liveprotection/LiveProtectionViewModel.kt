package com.scamshield.android.ui.liveprotection

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamshield.android.audio.AudioMonitorController
import com.scamshield.android.audio.LiveMonitoringState
import com.scamshield.android.data.preferences.AppPreferencesRepository
import com.scamshield.android.data.repository.ScamDetectionRepository
import com.scamshield.android.domain.model.AnalysisResult
import com.scamshield.android.domain.model.ScreeningEvent
import com.scamshield.android.notifications.ScamNotificationManager
import com.scamshield.android.service.LiveAudioAnalysisService
import com.scamshield.android.util.RoleAndPermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveProtectionUiState(
    val monitoring: LiveMonitoringState = LiveMonitoringState(),
    val manualTranscriptInput: String = "",
    val callerPhoneInput: String = "",
    val isAnalyzingText: Boolean = false,
    val textAnalysisError: String? = null,
    val contactsPermissionGranted: Boolean = false,
    val callScreeningRoleHeld: Boolean = false,
    val microphonePermissionGranted: Boolean = false,
    val phoneStatePermissionGranted: Boolean = false,
    val autoAnalyzeOnCallConnect: Boolean = true,
    val latestScreeningEvent: ScreeningEvent? = null,
    val showConsentDialog: Boolean = false
)

@HiltViewModel
class LiveProtectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioMonitorController: AudioMonitorController,
    private val detectionRepository: ScamDetectionRepository,
    private val notificationManager: ScamNotificationManager,
    private val preferencesRepository: AppPreferencesRepository
) : ViewModel() {

    private val localUi = MutableStateFlow(LocalFormState())

    private data class LocalFormState(
        val manualTranscriptInput: String = "",
        val callerPhoneInput: String = "",
        val isAnalyzingText: Boolean = false,
        val textAnalysisError: String? = null,
        val permissionTick: Int = 0,
        val showConsentDialog: Boolean = false
    )

    val uiState: StateFlow<LiveProtectionUiState> = combine(
        audioMonitorController.state,
        detectionRepository.screeningEventsFlow,
        preferencesRepository.settingsFlow,
        localUi
    ) { audioState, screenings, settings, form ->
        LiveProtectionUiState(
            monitoring = audioState,
            manualTranscriptInput = form.manualTranscriptInput,
            callerPhoneInput = form.callerPhoneInput,
            isAnalyzingText = form.isAnalyzingText,
            textAnalysisError = form.textAnalysisError,
            contactsPermissionGranted = RoleAndPermissionHelper.isContactsGranted(context),
            callScreeningRoleHeld = RoleAndPermissionHelper.isCallScreeningRoleHeld(context),
            microphonePermissionGranted = RoleAndPermissionHelper.isMicrophoneGranted(context),
            phoneStatePermissionGranted = RoleAndPermissionHelper.isPhoneStateGranted(context),
            autoAnalyzeOnCallConnect = settings.autoAnalyzeOnCallConnect,
            latestScreeningEvent = screenings.firstOrNull(),
            showConsentDialog = form.showConsentDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LiveProtectionUiState()
    )

    fun refreshPermissions() {
        localUi.update { it.copy(permissionTick = it.permissionTick + 1) }
    }

    fun setAutoAnalyzeOnCallConnect(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoAnalyzeOnCallConnect(enabled) }
    }

    fun updateManualTranscript(text: String) {
        localUi.update { it.copy(manualTranscriptInput = text, textAnalysisError = null) }
    }

    fun updateCallerPhoneInput(phone: String) {
        localUi.update { it.copy(callerPhoneInput = phone) }
        audioMonitorController.setTargetPhoneNumber(phone)
    }

    fun openConsentDialog() {
        localUi.update { it.copy(showConsentDialog = true) }
    }

    fun dismissConsentDialog() {
        localUi.update { it.copy(showConsentDialog = false) }
    }

    fun startLiveAudioMonitoring() {
        localUi.update { it.copy(showConsentDialog = false) }
        val phone = localUi.value.callerPhoneInput.trim()
        val intent = LiveAudioAnalysisService.startIntent(context, phoneNumber = phone)
        ContextCompat.startForegroundService(context, intent)
    }

    fun analyzeCurrentBufferNow() {
        context.startService(LiveAudioAnalysisService.analyzeNowIntent(context))
    }

    fun stopLiveAudioMonitoring() {
        context.startService(LiveAudioAnalysisService.stopIntent(context))
    }

    fun deleteLastClip() {
        val savedId = audioMonitorController.state.value.lastSavedRecording?.recordingId
        if (savedId != null) {
            viewModelScope.launch {
                detectionRepository.deleteCallRecording(savedId)
                audioMonitorController.clearLastLocalClipReference()
            }
        } else {
            audioMonitorController.clearLastLocalClipReference()
        }
    }

    fun dismissAudioError() {
        audioMonitorController.clearError()
    }

    fun analyzeManualTranscript(onResultReady: (AnalysisResult) -> Unit) {
        val text = localUi.value.manualTranscriptInput.trim()
        if (text.length < 5) {
            localUi.update {
                it.copy(textAnalysisError = "Please enter at least a sentence of call dialogue.")
            }
            return
        }
        val phone = localUi.value.callerPhoneInput.trim().takeIf { it.isNotBlank() }

        viewModelScope.launch {
            localUi.update { it.copy(isAnalyzingText = true, textAnalysisError = null) }
            val res = detectionRepository.analyzeTranscriptText(text, sourcePhoneNumber = phone)
            res.onSuccess { analysis ->
                localUi.update { it.copy(isAnalyzingText = false) }
                if (analysis.riskLevel.name != "LOW") {
                    notificationManager.notifyAnalysisResult(analysis)
                }
                onResultReady(analysis)
            }.onFailure { err ->
                localUi.update {
                    it.copy(
                        isAnalyzingText = false,
                        textAnalysisError = err.localizedMessage ?: "Failed to analyze transcript."
                    )
                }
            }
        }
    }
}

