package com.scamshield.android.ui.contacts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamshield.android.data.contacts.ContactsRepository
import com.scamshield.android.domain.model.TrustedContact
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

data class TrustedContactsUiState(
    val contactsPermissionGranted: Boolean = false,
    val contacts: List<TrustedContact> = emptyList(),
    val searchQuery: String = "",
    val isSyncing: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class TrustedContactsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactsRepository: ContactsRepository
) : ViewModel() {

    private val controls = MutableStateFlow(Controls())

    private data class Controls(
        val searchQuery: String = "",
        val isSyncing: Boolean = false,
        val statusMessage: String? = null,
        val permissionTick: Int = 0
    )

    val uiState: StateFlow<TrustedContactsUiState> = combine(
        contactsRepository.trustedContactsFlow,
        controls
    ) { contacts, ctrl ->
        val query = ctrl.searchQuery.trim()
        val filtered = if (query.isEmpty()) {
            contacts
        } else {
            contacts.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                    it.normalizedNumber.contains(query, ignoreCase = true) ||
                    it.rawNumber.contains(query, ignoreCase = true)
            }
        }
        TrustedContactsUiState(
            contactsPermissionGranted = RoleAndPermissionHelper.isContactsGranted(context),
            contacts = filtered,
            searchQuery = ctrl.searchQuery,
            isSyncing = ctrl.isSyncing,
            statusMessage = ctrl.statusMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrustedContactsUiState()
    )

    init {
        refreshPermissionAndSync()
    }

    fun updateSearchQuery(query: String) = controls.update { it.copy(searchQuery = query) }

    fun refreshPermissionAndSync() {
        controls.update { it.copy(permissionTick = it.permissionTick + 1) }
        if (RoleAndPermissionHelper.isContactsGranted(context)) {
            syncContactsNow()
        }
    }

    fun syncContactsNow() {
        viewModelScope.launch {
            controls.update { it.copy(isSyncing = true, statusMessage = null) }
            val res = contactsRepository.syncDeviceContacts()
            res.onSuccess { count ->
                controls.update {
                    it.copy(
                        isSyncing = false,
                        statusMessage = "$count trusted numbers synchronized locally on device."
                    )
                }
            }.onFailure { err ->
                controls.update {
                    it.copy(
                        isSyncing = false,
                        statusMessage = err.localizedMessage ?: "Unable to read local contacts."
                    )
                }
            }
        }
    }
}

