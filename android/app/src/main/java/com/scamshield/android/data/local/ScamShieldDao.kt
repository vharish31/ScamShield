package com.scamshield.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedContactDao {
    @Query("SELECT * FROM trusted_contacts WHERE lookup_key IN (:lookupKeys) LIMIT 1")
    suspend fun findFirstByLookupKeys(lookupKeys: List<String>): TrustedContactEntity?

    @Query("SELECT DISTINCT canonical_number, contact_id, display_name, raw_number, lookup_key, synced_at_millis FROM trusted_contacts GROUP BY contact_id, canonical_number ORDER BY display_name ASC")
    fun observeDistinctContacts(): Flow<List<TrustedContactEntity>>

    @Query("SELECT COUNT(DISTINCT canonical_number) FROM trusted_contacts")
    fun observeDistinctCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM trusted_contacts")
    suspend fun entryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TrustedContactEntity>)

    @Query("DELETE FROM trusted_contacts")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<TrustedContactEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }
}

@Dao
interface ScreeningEventDao {
    @Query("SELECT * FROM screening_events ORDER BY timestamp_millis DESC LIMIT 200")
    fun observeRecentEvents(): Flow<List<ScreeningEventEntity>>

    @Query("SELECT COUNT(*) FROM screening_events")
    fun observeTotalCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ScreeningEventEntity): Long

    @Query("DELETE FROM screening_events WHERE timestamp_millis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM screening_events")
    suspend fun clearAll()
}

@Dao
interface CachedAnalysisDao {
    @Query("SELECT * FROM cached_analyses ORDER BY cached_at_millis DESC")
    fun observeAll(): Flow<List<CachedAnalysisEntity>>

    @Query("SELECT * FROM cached_analyses WHERE analysis_id = :analysisId LIMIT 1")
    suspend fun getById(analysisId: String): CachedAnalysisEntity?

    @Query("SELECT * FROM cached_analyses WHERE source_phone_number = :normalizedNumber ORDER BY cached_at_millis DESC LIMIT 1")
    suspend fun findLatestByPhoneNumber(normalizedNumber: String): CachedAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedAnalysisEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedAnalysisEntity)

    @Query("DELETE FROM cached_analyses WHERE analysis_id = :analysisId")
    suspend fun deleteById(analysisId: String)

    @Query("DELETE FROM cached_analyses WHERE cached_at_millis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM cached_analyses")
    suspend fun clearAll()
}

@Dao
interface NotificationLogDao {
    @Query("SELECT * FROM notification_logs ORDER BY timestamp_millis DESC LIMIT 200")
    fun observeAll(): Flow<List<NotificationLogEntity>>

    @Query("SELECT COUNT(*) FROM notification_logs WHERE is_read = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_logs WHERE dedup_key = :dedupKey AND timestamp_millis >= :sinceMillis")
    suspend fun countRecentByDedupKey(dedupKey: String, sinceMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NotificationLogEntity): Long

    @Query("UPDATE notification_logs SET is_read = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notification_logs WHERE timestamp_millis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM notification_logs")
    suspend fun clearAll()
}

@Dao
interface CallRecordingDao {
    @Query("SELECT * FROM call_recordings ORDER BY timestamp_millis DESC LIMIT 200")
    fun observeAll(): Flow<List<CallRecordingEntity>>

    @Query("SELECT * FROM call_recordings WHERE recording_id = :recordingId LIMIT 1")
    suspend fun getById(recordingId: String): CallRecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CallRecordingEntity)

    @Query("DELETE FROM call_recordings WHERE recording_id = :recordingId")
    suspend fun deleteById(recordingId: String)

    @Query("SELECT * FROM call_recordings WHERE timestamp_millis < :cutoffMillis")
    suspend fun findOlderThan(cutoffMillis: Long): List<CallRecordingEntity>

    @Query("DELETE FROM call_recordings WHERE timestamp_millis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM call_recordings")
    suspend fun clearAll()
}

