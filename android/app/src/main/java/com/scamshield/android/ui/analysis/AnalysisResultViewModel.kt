package com.scamshield.android.ui.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamshield.android.data.repository.ScamDetectionRepository
import com.scamshield.android.domain.model.AnalysisResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalysisResultUiState(
    val isLoading: Boolean = true,
    val analysis: AnalysisResult? = null,
    val errorMessage: String? = null,
    val isDeleting: Boolean = false
)

@HiltViewModel
class AnalysisResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detectionRepository: ScamDetectionRepository
) : ViewModel() {

    private val analysisId: String = savedStateHandle.get<String>("analysisId").orEmpty()

    private val _uiState = MutableStateFlow(AnalysisResultUiState())
    val uiState: StateFlow<AnalysisResultUiState> = _uiState.asStateFlow()

    init {
        loadAnalysis()
    }

    fun loadAnalysis() {
        if (analysisId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid analysis ID.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = detectionRepository.getAnalysisDetail(analysisId)
            result.onSuccess { detail ->
                _uiState.update { it.copy(isLoading = false, analysis = detail) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Failed to load analysis detail."
                    )
                }
            }
        }
    }

    fun deleteAnalysis(onDeleted: () -> Unit) {
        if (analysisId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            detectionRepository.deleteHistoryItem(analysisId)
                .onSuccess {
                    _uiState.update { it.copy(isDeleting = false) }
                    onDeleted()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            errorMessage = err.localizedMessage ?: "Could not delete record."
                        )
                    }
                }
        }
    }
}

