package com.scamshield.android.ui.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamshield.android.domain.model.AlertType
import com.scamshield.android.domain.model.AppNotificationItem
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

data class NotificationsUiState(
    val selectedFilter: AlertType? = null,
    val notificationsPermissionGranted: Boolean = true,
    val items: List<AppNotificationItem> = emptyList()
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: ScamNotificationManager
) : ViewModel() {

    private val filterFlow = MutableStateFlow<AlertType?>(null)
    private val permissionTick = MutableStateFlow(0)

    val uiState: StateFlow<NotificationsUiState> = combine(
        notificationManager.notificationsFlow,
        filterFlow,
        permissionTick
    ) { allItems, filter, _ ->
        val filtered = if (filter == null) allItems else allItems.filter { it.alertType == filter }
        NotificationsUiState(
            selectedFilter = filter,
            notificationsPermissionGranted = RoleAndPermissionHelper.isNotificationsGranted(context),
            items = filtered
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationsUiState()
    )

    fun refreshPermission() {
        permissionTick.value += 1
    }

    fun selectFilter(type: AlertType?) {
        filterFlow.value = type
    }

    fun markAllRead() {
        viewModelScope.launch { notificationManager.markAllRead() }
    }

    fun clearAll() {
        viewModelScope.launch { notificationManager.clearAllLogs() }
    }
}

