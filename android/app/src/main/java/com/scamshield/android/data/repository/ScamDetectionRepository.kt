package com.scamshield.android.data.repository

import com.scamshield.android.data.local.CachedAnalysisDao
import com.scamshield.android.data.local.CachedAnalysisEntity
import com.scamshield.android.data.local.CallRecordingDao
import com.scamshield.android.data.local.CallRecordingEntity
import com.scamshield.android.data.local.NotificationLogDao
import com.scamshield.android.data.local.ScreeningEventDao
import com.scamshield.android.data.remote.ApiErrorParser
import com.scamshield.android.data.remote.ScamShieldApiService
import com.scamshield.android.data.remote.dto.AnalysisResponseDto
import com.scamshield.android.data.remote.dto.AnalyzeTextRequestDto
import com.scamshield.android.domain.contacts.PhoneNumberNormalizer
import com.scamshield.android.domain.model.AnalysisResult
import com.scamshield.android.domain.model.BackendHealth
import com.scamshield.android.domain.model.CallRecording
import com.scamshield.android.domain.model.CallerTrustStatus
import com.scamshield.android.domain.model.HistorySummary
import com.scamshield.android.domain.model.RecordingSyncStatus
import com.scamshield.android.domain.model.RiskLevel
import com.scamshield.android.domain.model.ScreeningEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScamDetectionRepository @Inject constructor(
    private val apiService: ScamShieldApiService,
    private val cachedAnalysisDao: CachedAnalysisDao,
    private val screeningEventDao: ScreeningEventDao,
    private val notificationLogDao: NotificationLogDao,
    private val callRecordingDao: CallRecordingDao
) {

    val cachedAnalysesFlow: Flow<List<AnalysisResult>> =
        cachedAnalysisDao.observeAll().map { list -> list.map { it.toDomain() } }

    val callRecordingsFlow: Flow<List<CallRecording>> =
        callRecordingDao.observeAll().map { list -> list.map { it.toDomain() } }

    val screeningEventsFlow: Flow<List<ScreeningEvent>> =
        screeningEventDao.observeRecentEvents().map { list ->
            list.map {
                ScreeningEvent(
                    id = it.id,
                    normalizedNumber = it.normalizedNumber,
                    displayLabel = it.displayLabel,
                    trustStatus = runCatching { CallerTrustStatus.valueOf(it.trustStatus) }
                        .getOrDefault(CallerTrustStatus.UNKNOWN),
                    contactsPermissionGranted = it.contactsPermissionGranted,
                    wasPriorFlagged = it.wasPriorFlagged,
                    priorRiskLevel = it.priorRiskLevel?.let { lvl -> RiskLevel.fromApiValue(lvl) },
                    timestampMillis = it.timestampMillis,
                    notes = it.notes
                )
            }
        }

    val screeningCountFlow: Flow<Int> = screeningEventDao.observeTotalCount()

    suspend fun checkBackendHealth(): Result<BackendHealth> = withContext(Dispatchers.IO) {
        try {
            val dto = apiService.getHealth()
            Result.success(
                BackendHealth(
                    status = dto.status,
                    version = dto.version,
                    firebaseConnected = dto.firebaseConnected,
                    whisperConfigured = dto.whisperConfigured,
                    mlModelStatus = dto.mlModelStatus
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorParser.toUserMessage(e), e))
        }
    }

    suspend fun analyzeTranscriptText(
        transcript: String,
        sourcePhoneNumber: String? = null
    ): Result<AnalysisResult> = withContext(Dispatchers.IO) {
        val cleaned = transcript.trim()
        if (cleaned.length < 3) {
            return@withContext Result.failure(
                IllegalArgumentException("Please enter at least a short conversation transcript to analyze.")
            )
        }
        try {
            val dto = apiService.analyzeText(AnalyzeTextRequestDto(cleaned))
            val canonicalPhone = PhoneNumberNormalizer.normalizeToCanonical(sourcePhoneNumber)
            val domain = dto.toDomain(canonicalPhone)
            cachedAnalysisDao.upsert(domain.toEntity())
            Result.success(domain)
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorParser.toUserMessage(e), e))
        }
    }

    suspend fun uploadAndAnalyzeAudio(
        audioFile: File,
        sourcePhoneNumber: String? = null
    ): Result<AnalysisResult> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("Audio recording file is empty or missing.")
            )
        }
        try {
            val filePart = buildAudioFilePart(audioFile)
            val uploaded = apiService.uploadAudio(filePart)
            val audioIdBody = uploaded.audioId.toRequestBody("text/plain".toMediaType())
            val dto = apiService.analyzeAudioById(audioIdBody)
            val canonicalPhone = PhoneNumberNormalizer.normalizeToCanonical(sourcePhoneNumber)
            val domain = dto.toDomain(canonicalPhone)
            cachedAnalysisDao.upsert(domain.toEntity())
            Result.success(domain)
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorParser.toUserMessage(e), e))
        }
    }

    /**
     * Single-request upload + transcription + scam analysis endpoint (/api/analyze/audio with file).
     * Eliminates a redundant HTTP round-trip on live rolling audio segments while still saving
     * both `audio_uploads` and `analysis_history` in backend Cloud Firestore.
     */
    suspend fun analyzeAudioDirect(
        audioFile: File,
        sourcePhoneNumber: String? = null
    ): Result<AnalysisResult> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("Audio recording file is empty or missing.")
            )
        }
        try {
            val filePart = buildAudioFilePart(audioFile)
            val dto = apiService.analyzeAudioFile(filePart)
            val canonicalPhone = PhoneNumberNormalizer.normalizeToCanonical(sourcePhoneNumber)
            val domain = dto.toDomain(canonicalPhone)
            cachedAnalysisDao.upsert(domain.toEntity())
            Result.success(domain)
        } catch (e: Exception) {
            Result.failure(AudioRequestException(ApiErrorParser.toUserMessage(e), e))
        }
    }

    /**
     * Persists a recorded call in Room immediately, uploads it to FastAPI/Firestore for both
     * audio storage (`audio_uploads`) and scam analysis (`analysis_history`), and falls back to
     * raw `/api/audio/upload` if Whisper VAD reports silent/unclear speech (HTTP 422).
     */
    suspend fun persistAndSyncCallRecording(
        recordingFile: File,
        durationSeconds: Int,
        sourcePhoneNumber: String? = null,
        existingRecordingId: String? = null,
        latestLiveAnalysis: AnalysisResult? = null
    ): CallRecording = withContext(Dispatchers.IO) {
        val recordingId = existingRecordingId ?: UUID.randomUUID().toString()
        val masked = sourcePhoneNumber?.takeIf { it.isNotBlank() }?.let {
            PhoneNumberNormalizer.maskNumberForPrivacy(it)
        }
        val fileSize = recordingFile.takeIf { it.exists() }?.length() ?: 0L
        val now = System.currentTimeMillis()

        val initialEntity = CallRecordingEntity(
            recordingId = recordingId,
            localFilePath = recordingFile.absolutePath,
            numberMasked = masked,
            durationSeconds = durationSeconds,
            fileSizeBytes = fileSize,
            audioId = latestLiveAnalysis?.audioId,
            analysisId = latestLiveAnalysis?.analysisId,
            riskLevel = latestLiveAnalysis?.riskLevel?.apiValue,
            riskScore = latestLiveAnalysis?.riskScore,
            transcriptSummary = latestLiveAnalysis?.transcript?.take(160)
                ?: "Recorded call saved locally. Uploading to backend...",
            syncStatus = RecordingSyncStatus.LOCAL_PENDING.name,
            errorNote = null,
            timestampMillis = now
        )
        callRecordingDao.upsert(initialEntity)

        if (!recordingFile.exists() || fileSize <= 44L) {
            val updated = initialEntity.copy(
                syncStatus = RecordingSyncStatus.FAILED.name,
                errorNote = "Recorded audio buffer was empty."
            )
            callRecordingDao.upsert(updated)
            return@withContext updated.toDomain()
        }

        val analyzeResult = analyzeAudioDirect(recordingFile, sourcePhoneNumber)
        if (analyzeResult.isSuccess) {
            val analysis = analyzeResult.getOrThrow()
            val synced = initialEntity.copy(
                audioId = analysis.audioId,
                analysisId = analysis.analysisId,
                riskLevel = analysis.riskLevel.apiValue,
                riskScore = analysis.riskScore,
                transcriptSummary = analysis.transcript.take(180).ifBlank {
                    "Call analyzed and saved to backend database."
                },
                syncStatus = RecordingSyncStatus.SYNCED_ANALYSIS.name,
                errorNote = null
            )
            callRecordingDao.upsert(synced)
            return@withContext synced.toDomain()
        }

        val failure = analyzeResult.exceptionOrNull()
        val rootCause = (failure as? AudioRequestException)?.cause ?: failure
        val isSilentSpeech422 = (rootCause as? HttpException)?.code() == 422

        if (isSilentSpeech422) {
            val rawUploadResult = runCatching {
                apiService.uploadAudio(buildAudioFilePart(recordingFile))
            }
            if (rawUploadResult.isSuccess) {
                val uploaded = rawUploadResult.getOrThrow()
                val audioOnly = initialEntity.copy(
                    audioId = uploaded.audioId,
                    syncStatus = RecordingSyncStatus.UPLOADED_AUDIO_ONLY.name,
                    transcriptSummary = latestLiveAnalysis?.transcript?.take(180)
                        ?: "Audio uploaded to database (no speech detected in recording).",
                    errorNote = "Stored audio in backend DB; Whisper detected insufficient clear speech for full analysis."
                )
                callRecordingDao.upsert(audioOnly)
                return@withContext audioOnly.toDomain()
            }
        }

        val pending = initialEntity.copy(
            syncStatus = RecordingSyncStatus.LOCAL_PENDING.name,
            transcriptSummary = latestLiveAnalysis?.transcript?.take(180)
                ?: "Saved on device. Tap Sync when backend connection is ready.",
            errorNote = failure?.message ?: "Backend sync failed."
        )
        callRecordingDao.upsert(pending)
        return@withContext pending.toDomain()
    }

    suspend fun retrySyncCallRecording(recordingId: String): CallRecording? =
        withContext(Dispatchers.IO) {
            val existing = callRecordingDao.getById(recordingId) ?: return@withContext null
            val file = File(existing.localFilePath)
            persistAndSyncCallRecording(
                recordingFile = file,
                durationSeconds = existing.durationSeconds,
                sourcePhoneNumber = existing.numberMasked,
                existingRecordingId = existing.recordingId
            )
        }

    suspend fun deleteCallRecording(recordingId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = callRecordingDao.getById(recordingId)
        if (existing?.audioId != null) {
            runCatching { apiService.deleteAudio(existing.audioId) }
        }
        if (existing != null) {
            runCatching { File(existing.localFilePath).delete() }
        }
        callRecordingDao.deleteById(recordingId)
        Result.success(Unit)
    }

    private fun buildAudioFilePart(audioFile: File): MultipartBody.Part {
        val mimeType = when (audioFile.extension.lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            else -> "application/octet-stream"
        }
        val requestBody = audioFile.asRequestBody(mimeType.toMediaType())
        return MultipartBody.Part.createFormData("file", audioFile.name, requestBody)
    }

    private class AudioRequestException(message: String, cause: Throwable) : Exception(message, cause)

    suspend fun fetchRemoteHistory(): Result<List<HistorySummary>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getHistory()
            val summaries = response.items.map { item ->
                HistorySummary(
                    analysisId = item.analysisId,
                    riskScore = item.riskScore,
                    riskLevel = RiskLevel.fromApiValue(item.riskLevel),
                    detectedPatterns = item.detectedPatterns,
                    transcriptPreview = item.transcriptPreview,
                    audioId = item.audioId,
                    createdAt = item.createdAt
                )
            }
            Result.success(summaries)
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorParser.toUserMessage(e), e))
        }
    }

    suspend fun getAnalysisDetail(analysisId: String): Result<AnalysisResult> =
        withContext(Dispatchers.IO) {
            val cached = cachedAnalysisDao.getById(analysisId)?.toDomain()
            try {
                val remote = apiService.getHistoryDetail(analysisId)
                    .toDomain(sourcePhoneNumber = cached?.sourcePhoneNumber)
                cachedAnalysisDao.upsert(remote.toEntity())
                Result.success(remote)
            } catch (e: Exception) {
                if (cached != null) {
                    Result.success(cached)
                } else {
                    Result.failure(Exception(ApiErrorParser.toUserMessage(e), e))
                }
            }
        }

    suspend fun deleteHistoryItem(analysisId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            runCatching { apiService.deleteHistoryItem(analysisId) }
            cachedAnalysisDao.deleteById(analysisId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrorParser.toUserMessage(e), e))
        }
    }

    suspend fun findPriorAnalysisForNumber(canonicalNumber: String): AnalysisResult? =
        withContext(Dispatchers.IO) {
            cachedAnalysisDao.findLatestByPhoneNumber(canonicalNumber)?.toDomain()
        }

    suspend fun purgeExpiredLocalData(retentionDays: Int): Unit = withContext(Dispatchers.IO) {
        val cutoffMillis = System.currentTimeMillis() - retentionDays.toLong() * 24L * 60L * 60L * 1000L
        screeningEventDao.deleteOlderThan(cutoffMillis)
        cachedAnalysisDao.deleteOlderThan(cutoffMillis)
        notificationLogDao.deleteOlderThan(cutoffMillis)
        val expiredRecordings = callRecordingDao.findOlderThan(cutoffMillis)
        expiredRecordings.forEach { rec -> runCatching { File(rec.localFilePath).delete() } }
        callRecordingDao.deleteOlderThan(cutoffMillis)
    }

    suspend fun clearAllLocalRecords(): Unit = withContext(Dispatchers.IO) {
        screeningEventDao.clearAll()
        cachedAnalysisDao.clearAll()
        notificationLogDao.clearAll()
        callRecordingDao.clearAll()
    }

    private fun AnalysisResponseDto.toDomain(sourcePhoneNumber: String?): AnalysisResult =
        AnalysisResult(
            analysisId = analysisId,
            transcript = transcript,
            riskScore = riskScore,
            riskLevel = RiskLevel.fromApiValue(riskLevel),
            detectedPatterns = detectedPatterns,
            recommendations = recommendations,
            isHeuristic = isHeuristic,
            confidenceDisclaimer = confidenceDisclaimer.ifBlank {
                "The risk score is a heuristic AI assessment, not proof of fraudulent intent."
            },
            audioId = audioId,
            createdAt = createdAt,
            sourcePhoneNumber = sourcePhoneNumber
        )

    private fun AnalysisResult.toEntity(): CachedAnalysisEntity = CachedAnalysisEntity(
        analysisId = analysisId,
        transcript = transcript,
        riskScore = riskScore,
        riskLevel = riskLevel.apiValue,
        detectedPatterns = detectedPatterns,
        recommendations = recommendations,
        isHeuristic = isHeuristic,
        confidenceDisclaimer = confidenceDisclaimer,
        audioId = audioId,
        createdAt = createdAt,
        sourcePhoneNumber = sourcePhoneNumber,
        cachedAtMillis = System.currentTimeMillis()
    )

    private fun CachedAnalysisEntity.toDomain(): AnalysisResult = AnalysisResult(
        analysisId = analysisId,
        transcript = transcript,
        riskScore = riskScore,
        riskLevel = RiskLevel.fromApiValue(riskLevel),
        detectedPatterns = detectedPatterns,
        recommendations = recommendations,
        isHeuristic = isHeuristic,
        confidenceDisclaimer = confidenceDisclaimer,
        audioId = audioId,
        createdAt = createdAt,
        sourcePhoneNumber = sourcePhoneNumber
    )

    private fun CallRecordingEntity.toDomain(): CallRecording = CallRecording(
        recordingId = recordingId,
        localFilePath = localFilePath,
        numberMasked = numberMasked,
        durationSeconds = durationSeconds,
        fileSizeBytes = fileSizeBytes,
        audioId = audioId,
        analysisId = analysisId,
        riskLevel = riskLevel?.let { RiskLevel.fromApiValue(it) },
        riskScore = riskScore,
        transcriptSummary = transcriptSummary,
        syncStatus = runCatching { RecordingSyncStatus.valueOf(syncStatus) }
            .getOrDefault(RecordingSyncStatus.LOCAL_PENDING),
        errorNote = errorNote,
        timestampMillis = timestampMillis
    )
}

