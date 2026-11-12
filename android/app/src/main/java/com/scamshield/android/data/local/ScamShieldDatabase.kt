package com.scamshield.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TrustedContactEntity::class,
        ScreeningEventEntity::class,
        CachedAnalysisEntity::class,
        NotificationLogEntity::class,
        CallRecordingEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class ScamShieldDatabase : RoomDatabase() {
    abstract fun trustedContactDao(): TrustedContactDao
    abstract fun screeningEventDao(): ScreeningEventDao
    abstract fun cachedAnalysisDao(): CachedAnalysisDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun callRecordingDao(): CallRecordingDao
}

