package com.scamshield.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamshield.android.data.repository.ScamDetectionRepository
import com.scamshield.android.domain.model.CallRecording
import com.scamshield.android.domain.model.HistorySummary
import com.scamshield.android.domain.model.RiskLevel
import com.scamshield.android.domain.model.ScreeningEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val selectedTabIndex: Int = 0,
    val selectedRiskFilter: RiskLevel? = null,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val syncingRecordingId: String? = null,
    val remoteHistory: List<HistorySummary> = emptyList(),
    val recordings: List<CallRecording> = emptyList(),
    val screeningEvents: List<ScreeningEvent> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val detectionRepository: ScamDetectionRepository
) : ViewModel() {

    private val localControls = MutableStateFlow(HistoryControls())
    private val remoteListFlow = MutableStateFlow<List<HistorySummary>>(emptyList())

    private data class HistoryControls(
        val selectedTabIndex: Int = 0,
        val selectedRiskFilter: RiskLevel? = null,
        val searchQuery: String = "",
        val isRefreshing: Boolean = false,
        val syncingRecordingId: String? = null,
        val errorMessage: String? = null
    )

    val uiState: StateFlow<HistoryUiState> = combine(
        localControls,
        remoteListFlow,
        detectionRepository.cachedAnalysesFlow,
        detectionRepository.callRecordingsFlow,
        detectionRepository.screeningEventsFlow
    ) { ctrl, remote, cached, recordings, screenings ->
        // Prefer live remote history when synced; fall back to local cached analyses seamlessly offline
        val sourceList = if (remote.isNotEmpty()) {
            remote
        } else {
            cached.map {
                HistorySummary(
                    analysisId = it.analysisId,
                    riskScore = it.riskScore,
                    riskLevel = it.riskLevel,
                    detectedPatterns = it.detectedPatterns,
                    transcriptPreview = it.transcript.take(180),
                    audioId = it.audioId,
                    createdAt = it.createdAt
                )
            }
        }

        val query = ctrl.searchQuery.trim()
        val filteredAnalyses = sourceList.filter { item ->
            val matchesRisk = ctrl.selectedRiskFilter == null || item.riskLevel == ctrl.selectedRiskFilter
            val matchesQuery = query.isEmpty() ||
                item.transcriptPreview.contains(query, ignoreCase = true) ||
                item.detectedPatterns.any { it.contains(query, ignoreCase = true) }
            matchesRisk && matchesQuery
        }

        val filteredRecordings = recordings.filter { rec ->
            query.isEmpty() ||
                rec.transcriptSummary.contains(query, ignoreCase = true) ||
                (rec.numberMasked?.contains(query, ignoreCase = true) == true)
        }

        val filteredScreenings = screenings.filter { evt ->
            query.isEmpty() ||
                evt.displayLabel.contains(query, ignoreCase = true) ||
                evt.notes.contains(query, ignoreCase = true)
        }

        HistoryUiState(
            selectedTabIndex = ctrl.selectedTabIndex,
            selectedRiskFilter = ctrl.selectedRiskFilter,
            searchQuery = ctrl.searchQuery,
            isRefreshing = ctrl.isRefreshing,
            syncingRecordingId = ctrl.syncingRecordingId,
            remoteHistory = filteredAnalyses,
            recordings = filteredRecordings,
            screeningEvents = filteredScreenings,
            errorMessage = ctrl.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    init {
        refreshHistory()
    }

    fun selectTab(index: Int) = localControls.update { it.copy(selectedTabIndex = index) }
    fun selectRiskFilter(level: RiskLevel?) = localControls.update { it.copy(selectedRiskFilter = level) }
    fun updateSearchQuery(query: String) = localControls.update { it.copy(searchQuery = query) }

    fun refreshHistory() {
        viewModelScope.launch {
            localControls.update { it.copy(isRefreshing = true, errorMessage = null) }
            val result = detectionRepository.fetchRemoteHistory()
            result.onSuccess { list ->
                remoteListFlow.value = list
                localControls.update { it.copy(isRefreshing = false) }
            }.onFailure { err ->
                localControls.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = err.localizedMessage
                    )
                }
            }
        }
    }

    fun deleteAnalysis(analysisId: String) {
        viewModelScope.launch {
            detectionRepository.deleteHistoryItem(analysisId).onSuccess {
                remoteListFlow.update { list -> list.filterNot { it.analysisId == analysisId } }
            }
        }
    }

    fun syncRecording(recordingId: String) {
        viewModelScope.launch {
            localControls.update { it.copy(syncingRecordingId = recordingId) }
            detectionRepository.retrySyncCallRecording(recordingId)
            localControls.update { it.copy(syncingRecordingId = null) }
            refreshHistory()
        }
    }

    fun deleteRecording(recordingId: String) {
        viewModelScope.launch {
            detectionRepository.deleteCallRecording(recordingId)
        }
    }
}

