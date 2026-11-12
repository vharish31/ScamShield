package com.scamshield.android.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HealthResponseDto(
    val status: String,
    val version: String,
    val timestamp: String = "",
    @SerialName("firebase_connected") val firebaseConnected: Boolean = false,
    @SerialName("whisper_configured") val whisperConfigured: Boolean = false,
    @SerialName("ml_model_status") val mlModelStatus: String = "unknown"
)

@Serializable
data class TokenVerifyRequestDto(
    val token: String
)

@Serializable
data class UserProfileDto(
    val uid: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("is_anonymous") val isAnonymous: Boolean = false,
    @SerialName("auth_time") val authTime: Long? = null
)

@Serializable
data class AudioUploadResponseDto(
    @SerialName("audio_id") val audioId: String,
    val filename: String,
    @SerialName("original_filename") val originalFilename: String,
    @SerialName("file_size_bytes") val fileSizeBytes: Long,
    val format: String,
    val status: String,
    @SerialName("upload_time") val uploadTime: String = ""
)

@Serializable
data class AnalyzeTextRequestDto(
    val text: String
)

@Serializable
data class AnalysisResponseDto(
    @SerialName("analysis_id") val analysisId: String,
    val transcript: String,
    @SerialName("risk_score") val riskScore: Int,
    @SerialName("risk_level") val riskLevel: String,
    @SerialName("detected_patterns") val detectedPatterns: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    @SerialName("is_heuristic") val isHeuristic: Boolean = true,
    @SerialName("confidence_disclaimer") val confidenceDisclaimer: String = "",
    @SerialName("audio_id") val audioId: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class HistoryItemResponseDto(
    @SerialName("analysis_id") val analysisId: String,
    @SerialName("user_id") val userId: String = "",
    @SerialName("risk_score") val riskScore: Int,
    @SerialName("risk_level") val riskLevel: String,
    @SerialName("detected_patterns") val detectedPatterns: List<String> = emptyList(),
    @SerialName("transcript_preview") val transcriptPreview: String = "",
    @SerialName("audio_id") val audioId: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class HistoryListResponseDto(
    val total: Int = 0,
    val items: List<HistoryItemResponseDto> = emptyList()
)

@Serializable
data class DeleteAudioResponseDto(
    val status: String,
    @SerialName("audio_id") val audioId: String
)

@Serializable
data class DeleteHistoryResponseDto(
    val status: String,
    @SerialName("analysis_id") val analysisId: String
)

@Serializable
data class ApiErrorEnvelopeDto(
    val error: ApiErrorBodyDto? = null,
    val detail: JsonElement? = null
)

@Serializable
data class ApiErrorBodyDto(
    val code: String = "API_ERROR",
    val message: String = "Request failed",
    val details: JsonElement? = null
)

