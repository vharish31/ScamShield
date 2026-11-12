package com.scamshield.android.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamshield.android.BuildConfig
import com.scamshield.android.data.auth.FirebaseAuthRepository
import com.scamshield.android.data.preferences.AppPreferencesRepository
import com.scamshield.android.data.preferences.UserSettings
import com.scamshield.android.data.repository.ScamDetectionRepository
import com.scamshield.android.domain.model.UserAccount
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

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val user: UserAccount? = null,
    val draftBackendUrl: String = BuildConfig.DEFAULT_BASE_URL,
    val draftDisplayName: String = "",
    val healthCheckMessage: String? = null,
    val isTestingConnection: Boolean = false,
    val callScreeningRoleHeld: Boolean = false,
    val actionFeedback: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: AppPreferencesRepository,
    private val authRepository: FirebaseAuthRepository,
    private val detectionRepository: ScamDetectionRepository
) : ViewModel() {

    private val localDrafts = MutableStateFlow(Drafts())

    private data class Drafts(
        val backendUrlDraft: String? = null,
        val displayNameDraft: String? = null,
        val healthCheckMessage: String? = null,
        val isTestingConnection: Boolean = false,
        val actionFeedback: String? = null,
        val roleTick: Int = 0
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.settingsFlow,
        authRepository.authStateFlow,
        localDrafts
    ) { settings, user, drafts ->
        SettingsUiState(
            settings = settings,
            user = user,
            draftBackendUrl = drafts.backendUrlDraft ?: settings.backendBaseUrl,
            draftDisplayName = drafts.displayNameDraft ?: user?.displayName.orEmpty(),
            healthCheckMessage = drafts.healthCheckMessage,
            isTestingConnection = drafts.isTestingConnection,
            callScreeningRoleHeld = RoleAndPermissionHelper.isCallScreeningRoleHeld(context),
            actionFeedback = drafts.actionFeedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun refreshRoleStatus() = localDrafts.update { it.copy(roleTick = it.roleTick + 1) }

    fun updateDraftUrl(url: String) = localDrafts.update {
        it.copy(backendUrlDraft = url, healthCheckMessage = null, actionFeedback = null)
    }

    fun updateDraftDisplayName(name: String) = localDrafts.update {
        it.copy(displayNameDraft = name, actionFeedback = null)
    }

    fun saveBackendUrl() {
        val candidate = uiState.value.draftBackendUrl.trim()
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            localDrafts.update {
                it.copy(healthCheckMessage = "URL must start with http:// or https://")
            }
            return
        }
        viewModelScope.launch {
            preferencesRepository.setBackendBaseUrl(candidate)
            localDrafts.update {
                it.copy(backendUrlDraft = null, actionFeedback = "Backend URL updated to $candidate")
            }
        }
    }

    fun testBackendConnection() {
        saveBackendUrl()
        viewModelScope.launch {
            localDrafts.update { it.copy(isTestingConnection = true, healthCheckMessage = null) }
            val result = detectionRepository.checkBackendHealth()
            result.onSuccess { health ->
                localDrafts.update {
                    it.copy(
                        isTestingConnection = false,
                        healthCheckMessage = "Connected! API v${health.version} • Whisper=${health.whisperConfigured} • ML=${health.mlModelStatus}"
                    )
                }
            }.onFailure { err ->
                localDrafts.update {
                    it.copy(
                        isTestingConnection = false,
                        healthCheckMessage = err.localizedMessage ?: "Connection failed."
                    )
                }
            }
        }
    }

    fun saveDisplayName() {
        val name = uiState.value.draftDisplayName.trim()
        if (name.length < 2) {
            localDrafts.update { it.copy(actionFeedback = "Display name must be at least 2 characters.") }
            return
        }
        viewModelScope.launch {
            authRepository.updateDisplayName(name)
                .onSuccess {
                    localDrafts.update {
                        it.copy(displayNameDraft = null, actionFeedback = "Profile display name updated.")
                    }
                }
                .onFailure { err ->
                    localDrafts.update {
                        it.copy(actionFeedback = err.localizedMessage ?: "Failed to update display name.")
                    }
                }
        }
    }

    fun setLiveScreeningEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setLiveScreeningEnabled(enabled) }
    }

    fun setAutoAnalyzeOnCallConnect(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoAnalyzeOnCallConnect(enabled) }
    }

    fun setLockScreenPrivacyEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setLockScreenPrivacyEnabled(enabled) }
    }

    fun setDarkModeOverride(darkMode: Boolean?) {
        viewModelScope.launch { preferencesRepository.setDarkModeOverride(darkMode) }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch { preferencesRepository.setRetentionDays(days) }
    }

    fun clearLocalData() {
        viewModelScope.launch {
            detectionRepository.clearAllLocalRecords()
            localDrafts.update {
                it.copy(actionFeedback = "Local screening logs, cached reports, and alerts cleared.")
            }
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        authRepository.signOut()
        onSignedOut()
    }
}

