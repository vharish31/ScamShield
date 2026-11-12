package com.scamshield.android.audio

import com.scamshield.android.domain.model.AnalysisResult
import com.scamshield.android.domain.model.CallRecording
import com.scamshield.android.domain.model.RecordingSyncStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class LiveMonitoringState(
    val isMonitoring: Boolean = false,
    val isFinalizingRecording: Boolean = false,
    val isUploadingChunk: Boolean = false,
    val elapsedSeconds: Int = 0,
    val audioAmplitude: Float = 0f,
    val audioSourceDescription: String = "Device Microphone (Speakerphone / Nearby Audio)",
    val targetPhoneNumber: String = "",
    val chunksUploaded: Int = 0,
    val latestAnalysis: AnalysisResult? = null,
    val transcriptSegments: List<String> = emptyList(),
    val lastRecordingPath: String? = null,
    val lastSavedRecording: CallRecording? = null,
    val statusMessage: String = "Ready for user-authorized live audio analysis.",
    val errorMessage: String? = null
)

@Singleton
class AudioMonitorController @Inject constructor() {
    private val _state = MutableStateFlow(LiveMonitoringState())
    val state: StateFlow<LiveMonitoringState> = _state.asStateFlow()

    private val _stopSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopSignal: SharedFlow<Unit> = _stopSignal.asSharedFlow()

    fun requestStopMonitoring() {
        _stopSignal.tryEmit(Unit)
    }

    fun setTargetPhoneNumber(number: String) {
        _state.update { it.copy(targetPhoneNumber = number) }
    }

    fun onMonitoringStarted(audioSourceLabel: String) {
        _state.update {
            it.copy(
                isMonitoring = true,
                isFinalizingRecording = false,
                isUploadingChunk = false,
                elapsedSeconds = 0,
                audioAmplitude = 0f,
                audioSourceDescription = audioSourceLabel,
                chunksUploaded = 0,
                transcriptSegments = emptyList(),
                lastSavedRecording = null,
                errorMessage = null,
                statusMessage = "Microphone active. Sending fast 4s rolling speech segments for live detection."
            )
        }
    }

    fun onTick(elapsedSeconds: Int, amplitude: Float) {
        _state.update {
            it.copy(
                elapsedSeconds = elapsedSeconds,
                audioAmplitude = amplitude
            )
        }
    }

    fun onChunkUploadStarted() {
        _state.update {
            it.copy(
                isUploadingChunk = true,
                statusMessage = "Analyzing live speech segment..."
            )
        }
    }

    fun onChunkAnalyzed(result: AnalysisResult) {
        _state.update { current ->
            val updatedTranscripts = if (result.transcript.isNotBlank()) {
                current.transcriptSegments + result.transcript
            } else {
                current.transcriptSegments
            }
            current.copy(
                isUploadingChunk = false,
                chunksUploaded = current.chunksUploaded + 1,
                latestAnalysis = result,
                transcriptSegments = updatedTranscripts,
                errorMessage = null,
                statusMessage = "Live update #${current.chunksUploaded + 1}: ${result.riskLevel.label} (${result.riskScore}/100)"
            )
        }
    }

    fun onSilentChunkSkipped(reason: String) {
        _state.update {
            it.copy(
                isUploadingChunk = false,
                statusMessage = reason
            )
        }
    }

    fun onChunkError(message: String) {
        _state.update {
            it.copy(
                isUploadingChunk = false,
                errorMessage = message,
                statusMessage = "Backend sync issue encountered."
            )
        }
    }

    fun onFinalizingRecording() {
        _state.update {
            it.copy(
                isMonitoring = false,
                isFinalizingRecording = true,
                audioAmplitude = 0f,
                statusMessage = "Saving full call recording to database..."
            )
        }
    }

    fun onRecordingPersisted(recording: CallRecording) {
        val syncSummary = when (recording.syncStatus) {
            RecordingSyncStatus.SYNCED_ANALYSIS ->
                "Call recording & analysis saved to database."
            RecordingSyncStatus.UPLOADED_AUDIO_ONLY ->
                "Recorded call audio uploaded to database (silent/quiet speech)."
            RecordingSyncStatus.LOCAL_PENDING ->
                "Recorded call saved on device (backend unreachable — tap Sync in History)."
            RecordingSyncStatus.FAILED ->
                recording.errorNote ?: "Call recording could not be saved."
        }
        _state.update {
            it.copy(
                isMonitoring = false,
                isFinalizingRecording = false,
                isUploadingChunk = false,
                audioAmplitude = 0f,
                lastRecordingPath = recording.localFilePath,
                lastSavedRecording = recording,
                statusMessage = syncSummary,
                errorMessage = if (recording.syncStatus == RecordingSyncStatus.LOCAL_PENDING ||
                    recording.syncStatus == RecordingSyncStatus.FAILED
                ) {
                    recording.errorNote
                } else {
                    it.errorMessage
                }
            )
        }
    }

    fun onRecordingClipSaved(path: String?) {
        _state.update { it.copy(lastRecordingPath = path) }
    }

    fun onMonitoringStopped(reason: String = "Live audio monitoring stopped.") {
        _state.update {
            it.copy(
                isMonitoring = false,
                isFinalizingRecording = false,
                isUploadingChunk = false,
                audioAmplitude = 0f,
                statusMessage = reason
            )
        }
    }

    fun clearLastLocalClipReference() {
        val path = _state.value.lastRecordingPath
        if (path != null) {
            runCatching { File(path).delete() }
        }
        _state.update { it.copy(lastRecordingPath = null, lastSavedRecording = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
