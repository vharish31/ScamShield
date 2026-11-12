package com.scamshield.android.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamshield.android.data.contacts.ContactsRepository
import com.scamshield.android.data.preferences.AppPreferencesRepository
import com.scamshield.android.util.RoleAndPermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val contactsGranted: Boolean = false,
    val microphoneGranted: Boolean = false,
    val phoneStateGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val callScreeningHeld: Boolean = false,
    val callScreeningSupported: Boolean = true,
    val syncedContactsCount: Int = 0,
    val statusMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactsRepository: ContactsRepository,
    private val preferencesRepository: AppPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionStatus()
    }

    fun refreshPermissionStatus() {
        val contactsOk = RoleAndPermissionHelper.isContactsGranted(context)
        _uiState.update {
            it.copy(
                contactsGranted = contactsOk,
                microphoneGranted = RoleAndPermissionHelper.isMicrophoneGranted(context),
                phoneStateGranted = RoleAndPermissionHelper.isPhoneStateGranted(context),
                notificationsGranted = RoleAndPermissionHelper.isNotificationsGranted(context),
                callScreeningHeld = RoleAndPermissionHelper.isCallScreeningRoleHeld(context),
                callScreeningSupported = RoleAndPermissionHelper.isCallScreeningRoleSupported()
            )
        }
        if (contactsOk) {
            syncContactsNow()
        }
    }

    fun syncContactsNow() {
        viewModelScope.launch {
            val result = contactsRepository.syncDeviceContacts()
            result.onSuccess { count ->
                _uiState.update {
                    it.copy(
                        syncedContactsCount = count,
                        statusMessage = "$count trusted contact numbers indexed locally on your device."
                    )
                }
            }
        }
    }

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
            onDone()
        }
    }
}

