package com.scamshield.android.worker

import android.content.Context
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scamshield.android.data.preferences.AppPreferencesRepository
import com.scamshield.android.data.repository.ScamDetectionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

class RetentionCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun preferencesRepository(): AppPreferencesRepository
        fun detectionRepository(): ScamDetectionRepository
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                WorkerEntryPoint::class.java
            )
            val settings = entryPoint.preferencesRepository().settingsFlow.first()
            entryPoint.detectionRepository().purgeExpiredLocalData(settings.retentionDays)

            // Sweep orphaned live audio temp files older than 1 day
            val chunkDir = File(applicationContext.cacheDir, "live_audio_chunks")
            if (chunkDir.exists()) {
                val oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
                chunkDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < oneDayAgo) {
                        runCatching { file.delete() }
                    }
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "scam_shield_retention_cleanup"

        fun schedule(context: Context) {
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(context, Configuration.Builder().build())
            }
            val request = PeriodicWorkRequestBuilder<RetentionCleanupWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

