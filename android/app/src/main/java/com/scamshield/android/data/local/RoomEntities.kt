package com.scamshield.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(raw: String): List<String> =
        if (raw.isBlank()) emptyList()
        else try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
}

/**
 * Local-only lookup index derived from device ContactsContract.
 * Never synced or uploaded to remote backend APIs.
 */
@Entity(
    tableName = "trusted_contacts",
    indices = [
        Index(value = ["lookup_key"]),
        Index(value = ["canonical_number"])
    ]
)
data class TrustedContactEntity(
    @PrimaryKey
    @ColumnInfo(name = "lookup_key")
    val lookupKey: String,
    @ColumnInfo(name = "contact_id")
    val contactId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "raw_number")
    val rawNumber: String,
    @ColumnInfo(name = "canonical_number")
    val canonicalNumber: String,
    @ColumnInfo(name = "synced_at_millis")
    val syncedAtMillis: Long
)

/**
 * Local audit log of screened unknown/unavailable incoming calls.
 * Trusted contact calls are excluded and never recorded as alerts.
 */
@Entity(
    tableName = "screening_events",
    indices = [Index(value = ["normalized_number"]), Index(value = ["timestamp_millis"])]
)
data class ScreeningEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "normalized_number")
    val normalizedNumber: String,
    @ColumnInfo(name = "display_label")
    val displayLabel: String,
    @ColumnInfo(name = "trust_status")
    val trustStatus: String,
    @ColumnInfo(name = "contacts_permission_granted")
    val contactsPermissionGranted: Boolean,
    @ColumnInfo(name = "was_prior_flagged")
    val wasPriorFlagged: Boolean,
    @ColumnInfo(name = "prior_risk_level")
    val priorRiskLevel: String?,
    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long,
    val notes: String
)

/**
 * Offline cache of backend scam analyses linked optionally to a normalized phone number
 * for local cross-checking of repeat unknown callers.
 */
@Entity(
    tableName = "cached_analyses",
    indices = [Index(value = ["source_phone_number"]), Index(value = ["cached_at_millis"])]
)
data class CachedAnalysisEntity(
    @PrimaryKey
    @ColumnInfo(name = "analysis_id")
    val analysisId: String,
    val transcript: String,
    @ColumnInfo(name = "risk_score")
    val riskScore: Int,
    @ColumnInfo(name = "risk_level")
    val riskLevel: String,
    @ColumnInfo(name = "detected_patterns")
    val detectedPatterns: List<String>,
    val recommendations: List<String>,
    @ColumnInfo(name = "is_heuristic")
    val isHeuristic: Boolean,
    @ColumnInfo(name = "confidence_disclaimer")
    val confidenceDisclaimer: String,
    @ColumnInfo(name = "audio_id")
    val audioId: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "source_phone_number")
    val sourcePhoneNumber: String?,
    @ColumnInfo(name = "cached_at_millis")
    val cachedAtMillis: Long
)

@Entity(
    tableName = "notification_logs",
    indices = [Index(value = ["dedup_key", "timestamp_millis"])]
)
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "alert_type")
    val alertType: String,
    val title: String,
    val message: String,
    @ColumnInfo(name = "number_masked")
    val numberMasked: String?,
    @ColumnInfo(name = "risk_level")
    val riskLevel: String?,
    @ColumnInfo(name = "analysis_id")
    val analysisId: String?,
    @ColumnInfo(name = "dedup_key")
    val dedupKey: String,
    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long,
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false
)

/**
 * Persistent local record of user-authorized recorded call audio files and their
 * remote Firestore/FastAPI sync state (analysis + audio_uploads).
 */
@Entity(
    tableName = "call_recordings",
    indices = [Index(value = ["timestamp_millis"]), Index(value = ["sync_status"])]
)
data class CallRecordingEntity(
    @PrimaryKey
    @ColumnInfo(name = "recording_id")
    val recordingId: String,
    @ColumnInfo(name = "local_file_path")
    val localFilePath: String,
    @ColumnInfo(name = "number_masked")
    val numberMasked: String?,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
    @ColumnInfo(name = "audio_id")
    val audioId: String?,
    @ColumnInfo(name = "analysis_id")
    val analysisId: String?,
    @ColumnInfo(name = "risk_level")
    val riskLevel: String?,
    @ColumnInfo(name = "risk_score")
    val riskScore: Int?,
    @ColumnInfo(name = "transcript_summary")
    val transcriptSummary: String,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String,
    @ColumnInfo(name = "error_note")
    val errorNote: String?,
    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long
)

