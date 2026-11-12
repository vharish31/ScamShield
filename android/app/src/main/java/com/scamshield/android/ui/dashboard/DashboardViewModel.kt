package com.scamshield.android.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamshield.android.audio.AudioMonitorController
import com.scamshield.android.data.auth.FirebaseAuthRepository
import com.scamshield.android.data.contacts.ContactsRepository
import com.scamshield.android.data.repository.ScamDetectionRepository
import com.scamshield.android.domain.model.AnalysisResult
import com.scamshield.android.domain.model.RiskLevel
import com.scamshield.android.domain.model.ScreeningEvent
import com.scamshield.android.domain.model.UserAccount
import com.scamshield.android.notifications.ScamNotificationManager
import com.scamshield.android.util.RoleAndPermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val user: UserAccount? = null,
    val trustedContactsCount: Int = 0,
    val unknownCallsScreenedCount: Int = 0,
    val analysesCompletedCount: Int = 0,
    val flaggedThreatsCount: Int = 0,
    val contactsPermissionGranted: Boolean = false,
    val callScreeningRoleHeld: Boolean = false,
    val notificationsPermissionGranted: Boolean = true,
    val isLiveAudioActive: Boolean = false,
    val unreadAlertsCount: Int = 0,
    val recentAnalyses: List<AnalysisResult> = emptyList(),
    val recentScreenings: List<ScreeningEvent> = emptyList(),
    val backendOnline: Boolean? = null,
    val backendStatusText: String = "Checking server connection..."
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: FirebaseAuthRepository,
    private val contactsRepository: ContactsRepository,
    private val detectionRepository: ScamDetectionRepository,
    private val notificationManager: ScamNotificationManager,
    audioMonitorController: AudioMonitorController
) : ViewModel() {

    private val permissionTick = MutableStateFlow(0)
    private val backendStatus = MutableStateFlow<Pair<Boolean?, String>>(null to "Tap refresh to check server")

    private val countsFlow = combine(
        authRepository.authStateFlow,
        contactsRepository.trustedCountFlow,
        detectionRepository.screeningCountFlow,
        detectionRepository.cachedAnalysesFlow,
        notificationManager.unreadCountFlow
    ) { user, trustedCount, screenedCount, analyses, unread ->
        listOf(user, trustedCount, screenedCount, analyses, unread)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        countsFlow,
        detectionRepository.screeningEventsFlow,
        audioMonitorController.state,
        permissionTick,
        backendStatus
    ) { base, screenings, liveAudio, _, healthPair ->
        val user = base[0] as UserAccount?
        val trustedCount = base[1] as Int
        val screenedCount = base[2] as Int
        @Suppress("UNCHECKED_CAST")
        val analyses = base[3] as List<AnalysisResult>
        val unread = base[4] as Int

        val flaggedCount = analyses.count {
            it.riskLevel == RiskLevel.HIGH || it.riskLevel == RiskLevel.CRITICAL || it.riskLevel == RiskLevel.MEDIUM
        }

        DashboardUiState(
            user = user,
            trustedContactsCount = trustedCount,
            unknownCallsScreenedCount = screenedCount,
            analysesCompletedCount = analyses.size,
            flaggedThreatsCount = flaggedCount,
            contactsPermissionGranted = RoleAndPermissionHelper.isContactsGranted(context),
            callScreeningRoleHeld = RoleAndPermissionHelper.isCallScreeningRoleHeld(context),
            notificationsPermissionGranted = RoleAndPermissionHelper.isNotificationsGranted(context),
            isLiveAudioActive = liveAudio.isMonitoring,
            unreadAlertsCount = unread,
            recentAnalyses = analyses.take(5),
            recentScreenings = screenings.take(5),
            backendOnline = healthPair.first,
            backendStatusText = healthPair.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        permissionTick.value += 1
        viewModelScope.launch {
            if (RoleAndPermissionHelper.isContactsGranted(context)) {
                contactsRepository.syncDeviceContacts()
            }
            val healthResult = detectionRepository.checkBackendHealth()
            healthResult.onSuccess { health ->
                val whisperNote = if (health.whisperConfigured) "STT Ready" else "Text AI Only"
                backendStatus.value = true to "Connected (v${health.version}, $whisperNote)"
            }.onFailure { err ->
                backendStatus.value = false to (err.localizedMessage ?: "Backend unreachable")
            }
        }
    }
}

