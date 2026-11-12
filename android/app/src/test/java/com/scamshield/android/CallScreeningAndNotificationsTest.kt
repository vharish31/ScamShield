package com.scamshield.android

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scamshield.android.data.contacts.ContactsRepository
import com.scamshield.android.data.local.CachedAnalysisEntity
import com.scamshield.android.data.local.ScamShieldDatabase
import com.scamshield.android.data.local.TrustedContactEntity
import com.scamshield.android.data.preferences.AppPreferencesRepository
import com.scamshield.android.data.remote.ScamShieldApiService
import com.scamshield.android.data.repository.ScamDetectionRepository
import com.scamshield.android.domain.model.CallerTrustStatus
import com.scamshield.android.domain.model.RiskLevel
import com.scamshield.android.notifications.ScamNotificationManager
import com.scamshield.android.service.CallScreeningDecision
import com.scamshield.android.service.CallScreeningProcessor
import com.scamshield.android.service.CallSessionTracker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallScreeningAndNotificationsTest {

    private lateinit var context: Application
    private lateinit var database: ScamShieldDatabase
    private lateinit var contactsRepository: ContactsRepository
    private lateinit var notificationManager: ScamNotificationManager
    private lateinit var processor: CallScreeningProcessor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ScamShieldDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val preferencesRepository = AppPreferencesRepository(context)
        contactsRepository = ContactsRepository(context, database.trustedContactDao())
        notificationManager = ScamNotificationManager(
            context = context,
            notificationLogDao = database.notificationLogDao(),
            preferencesRepository = preferencesRepository
        )

        val stubRetrofit = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:8000/")
            .client(OkHttpClient())
            .build()
            .create(ScamShieldApiService::class.java)

        val detectionRepo = ScamDetectionRepository(
            apiService = stubRetrofit,
            cachedAnalysisDao = database.cachedAnalysisDao(),
            screeningEventDao = database.screeningEventDao(),
            notificationLogDao = database.notificationLogDao(),
            callRecordingDao = database.callRecordingDao()
        )

        processor = CallScreeningProcessor(
            preferencesRepository = preferencesRepository,
            contactsRepository = contactsRepository,
            detectionRepository = detectionRepo,
            screeningEventDao = database.screeningEventDao(),
            notificationManager = notificationManager,
            callSessionTracker = CallSessionTracker()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saved trusted contacts bypass screening with zero events and zero notifications`() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        database.trustedContactDao().insertAll(
            listOf(
                TrustedContactEntity(
                    lookupKey = "+919876543210",
                    contactId = "c-1",
                    displayName = "Mom",
                    rawNumber = "+91 98765 43210",
                    canonicalNumber = "+919876543210",
                    syncedAtMillis = System.currentTimeMillis()
                ),
                TrustedContactEntity(
                    lookupKey = "9876543210",
                    contactId = "c-1",
                    displayName = "Mom",
                    rawNumber = "+91 98765 43210",
                    canonicalNumber = "+919876543210",
                    syncedAtMillis = System.currentTimeMillis()
                )
            )
        )

        val decision = processor.screenIncomingCall(
            rawNumber = "09876543210",
            isRestrictedOrPrivatePresentation = false
        )

        assertThat(decision).isInstanceOf(CallScreeningDecision.TrustedContactBypassed::class.java)
        assertThat((decision as CallScreeningDecision.TrustedContactBypassed).contactName).isEqualTo("Mom")
        assertThat(database.screeningEventDao().observeRecentEvents().first()).isEmpty()
        assertThat(database.notificationLogDao().observeAll().first()).isEmpty()
    }

    @Test
    fun `unknown caller logs local screening event and suppresses duplicate alerts`() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        database.trustedContactDao().insertAll(
            listOf(
                TrustedContactEntity(
                    lookupKey = "+919876543210",
                    contactId = "c-1",
                    displayName = "Mom",
                    rawNumber = "+919876543210",
                    canonicalNumber = "+919876543210",
                    syncedAtMillis = System.currentTimeMillis()
                )
            )
        )

        // First call from unsaved number triggers screening + alert
        val firstDecision = processor.screenIncomingCall(
            rawNumber = "+919900112233",
            isRestrictedOrPrivatePresentation = false
        )
        // Immediate repeat within 60s should suppress duplicate alert log
        processor.screenIncomingCall(
            rawNumber = "+919900112233",
            isRestrictedOrPrivatePresentation = false
        )

        assertThat(firstDecision).isInstanceOf(CallScreeningDecision.UnknownCallerScreened::class.java)
        val screened = firstDecision as CallScreeningDecision.UnknownCallerScreened
        assertThat(screened.trustStatus).isEqualTo(CallerTrustStatus.UNKNOWN)
        assertThat(screened.contactsPermissionGranted).isTrue()

        val logs = database.notificationLogDao().observeAll().first()
        assertThat(logs).hasSize(1)
    }

    @Test
    fun `previously flagged local analysis elevates screening warning`() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        database.trustedContactDao().insertAll(
            listOf(
                TrustedContactEntity(
                    lookupKey = "+919111111111",
                    contactId = "c-2",
                    displayName = "Friend",
                    rawNumber = "9111111111",
                    canonicalNumber = "+919111111111",
                    syncedAtMillis = System.currentTimeMillis()
                )
            )
        )
        database.cachedAnalysisDao().upsert(
            CachedAnalysisEntity(
                analysisId = "prev-high",
                transcript = "Transfer now for courier release",
                riskScore = 78,
                riskLevel = "HIGH",
                detectedPatterns = listOf("financial_pressure"),
                recommendations = listOf("Do not pay"),
                isHeuristic = true,
                confidenceDisclaimer = "Heuristic only",
                audioId = null,
                createdAt = "2026-11-11",
                sourcePhoneNumber = "+919988776655",
                cachedAtMillis = System.currentTimeMillis()
            )
        )

        val decision = processor.screenIncomingCall("9988776655", isRestrictedOrPrivatePresentation = false)
            as CallScreeningDecision.UnknownCallerScreened

        assertThat(decision.priorRiskLevel).isEqualTo(RiskLevel.HIGH)
        val notifications = database.notificationLogDao().observeAll().first()
        assertThat(notifications.first().title).contains("High-Risk")
    }

    @Test
    fun `denied contacts permission records honest disclosure and private number handled safely`() = runTest {
        shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS)

        val decision = processor.screenIncomingCall(
            rawNumber = "+919876543210",
            isRestrictedOrPrivatePresentation = true
        ) as CallScreeningDecision.UnknownCallerScreened

        assertThat(decision.trustStatus).isEqualTo(CallerTrustStatus.UNAVAILABLE_NUMBER)
        assertThat(decision.contactsPermissionGranted).isFalse()

        val events = database.screeningEventDao().observeRecentEvents().first()
        assertThat(events.first().notes).contains("Contacts permission denied")
    }
}

