package com.scamshield.android

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scamshield.android.data.local.CachedAnalysisEntity
import com.scamshield.android.data.local.NotificationLogEntity
import com.scamshield.android.data.local.ScamShieldDatabase
import com.scamshield.android.data.local.ScreeningEventEntity
import com.scamshield.android.data.preferences.AppPreferencesRepository
import com.scamshield.android.data.remote.ScamShieldApiService
import com.scamshield.android.data.repository.ScamDetectionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferencesAndRetentionTest {

    private lateinit var context: Application
    private lateinit var database: ScamShieldDatabase
    private lateinit var preferencesRepository: AppPreferencesRepository
    private lateinit var detectionRepository: ScamDetectionRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ScamShieldDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesRepository = AppPreferencesRepository(context)
        val stubApi = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:8000/")
            .client(OkHttpClient())
            .build()
            .create(ScamShieldApiService::class.java)
        detectionRepository = ScamDetectionRepository(
            apiService = stubApi,
            cachedAnalysisDao = database.cachedAnalysisDao(),
            screeningEventDao = database.screeningEventDao(),
            notificationLogDao = database.notificationLogDao(),
            callRecordingDao = database.callRecordingDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `preferences persist edited backend url and privacy toggles`() = runTest {
        preferencesRepository.setBackendBaseUrl("http://192.168.1.50:8000/")
        preferencesRepository.setLockScreenPrivacyEnabled(false)
        preferencesRepository.setRetentionDays(90)

        val settings = preferencesRepository.settingsFlow.first()
        assertThat(settings.backendBaseUrl).isEqualTo("http://192.168.1.50:8000")
        assertThat(preferencesRepository.getBaseUrl()).isEqualTo("http://192.168.1.50:8000")
        assertThat(settings.lockScreenPrivacyEnabled).isFalse()
        assertThat(settings.retentionDays).isEqualTo(90)
    }

    @Test
    fun `retention purge deletes stale entries while keeping recent records`() = runTest {
        val now = System.currentTimeMillis()
        val oldTimestamp = now - TimeUnit.DAYS.toMillis(40)
        val recentTimestamp = now - TimeUnit.DAYS.toMillis(2)

        database.screeningEventDao().insert(
            ScreeningEventEntity(
                normalizedNumber = "+919800000001",
                displayLabel = "+91••••0001",
                trustStatus = "UNKNOWN",
                contactsPermissionGranted = true,
                wasPriorFlagged = false,
                priorRiskLevel = null,
                timestampMillis = oldTimestamp,
                notes = "stale"
            )
        )
        database.screeningEventDao().insert(
            ScreeningEventEntity(
                normalizedNumber = "+919800000002",
                displayLabel = "+91••••0002",
                trustStatus = "UNKNOWN",
                contactsPermissionGranted = true,
                wasPriorFlagged = false,
                priorRiskLevel = null,
                timestampMillis = recentTimestamp,
                notes = "fresh"
            )
        )
        database.cachedAnalysisDao().upsert(
            CachedAnalysisEntity(
                analysisId = "old-id",
                transcript = "stale",
                riskScore = 20,
                riskLevel = "LOW",
                detectedPatterns = emptyList(),
                recommendations = emptyList(),
                isHeuristic = true,
                confidenceDisclaimer = "test",
                audioId = null,
                createdAt = "2026-09-01",
                sourcePhoneNumber = null,
                cachedAtMillis = oldTimestamp
            )
        )
        database.notificationLogDao().insert(
            NotificationLogEntity(
                alertType = "UNKNOWN_CALLER",
                title = "stale alert",
                message = "msg",
                numberMasked = null,
                riskLevel = null,
                analysisId = null,
                dedupKey = "old",
                timestampMillis = oldTimestamp
            )
        )

        detectionRepository.purgeExpiredLocalData(retentionDays = 30)

        val remainingScreenings = database.screeningEventDao().observeRecentEvents().first()
        assertThat(remainingScreenings).hasSize(1)
        assertThat(remainingScreenings.first().notes).isEqualTo("fresh")
        assertThat(database.cachedAnalysisDao().getById("old-id")).isNull()
        assertThat(database.notificationLogDao().observeAll().first()).isEmpty()
    }
}

