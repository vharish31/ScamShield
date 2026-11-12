package com.scamshield.android

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scamshield.android.data.local.ScamShieldDatabase
import com.scamshield.android.data.remote.ScamShieldApiService
import com.scamshield.android.data.repository.ScamDetectionRepository
import com.scamshield.android.domain.model.RecordingSyncStatus
import com.scamshield.android.domain.model.RiskLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScamDetectionRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var database: ScamShieldDatabase
    private lateinit var repository: ScamDetectionRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ScamShieldDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
        val apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ScamShieldApiService::class.java)

        repository = ScamDetectionRepository(
            apiService = apiService,
            cachedAnalysisDao = database.cachedAnalysisDao(),
            screeningEventDao = database.screeningEventDao(),
            notificationLogDao = database.notificationLogDao(),
            callRecordingDao = database.callRecordingDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `analyzeTranscriptText parses backend response and caches in Room`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "analysis_id": "an-123",
                      "transcript": "Share your OTP immediately or your account will be blocked.",
                      "risk_score": 88,
                      "risk_level": "CRITICAL",
                      "detected_patterns": ["credential_harvesting", "urgency", "account_suspension"],
                      "recommendations": ["Never share your OTP."],
                      "is_heuristic": true,
                      "confidence_disclaimer": "Heuristic risk assessment only.",
                      "audio_id": null,
                      "created_at": "2026-11-11T10:00:00Z"
                    }
                    """.trimIndent()
                )
        )

        val result = repository.analyzeTranscriptText(
            transcript = "Share your OTP immediately or your account will be blocked.",
            sourcePhoneNumber = "9876543210"
        )

        assertThat(result.isSuccess).isTrue()
        val data = result.getOrThrow()
        assertThat(data.analysisId).isEqualTo("an-123")
        assertThat(data.riskLevel).isEqualTo(RiskLevel.CRITICAL)
        assertThat(data.detectedPatterns).containsExactly(
            "credential_harvesting",
            "urgency",
            "account_suspension"
        )

        val cached = repository.findPriorAnalysisForNumber("+919876543210")
        assertThat(cached?.analysisId).isEqualTo("an-123")
        assertThat(cached?.riskScore).isEqualTo(88)
    }

    @Test
    fun `uploadAndAnalyzeAudio uploads audio and triggers audio analysis`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """
                    {
                      "audio_id": "aud-999",
                      "filename": "aud-999.wav",
                      "original_filename": "chunk.wav",
                      "file_size_bytes": 1024,
                      "format": "wav",
                      "status": "uploaded",
                      "upload_time": "2026-11-11T10:01:00Z"
                    }
                    """.trimIndent()
                )
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "analysis_id": "an-audio-1",
                      "transcript": "This is CBI police department calling.",
                      "risk_score": 72,
                      "risk_level": "HIGH",
                      "detected_patterns": ["authority_impersonation"],
                      "recommendations": ["Hang up and verify independently."],
                      "is_heuristic": true,
                      "confidence_disclaimer": "Heuristic only.",
                      "audio_id": "aud-999",
                      "created_at": "2026-11-11T10:01:05Z"
                    }
                    """.trimIndent()
                )
        )

        val wavFile = tempFolder.newFile("chunk.wav")
        wavFile.writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x41, 0x56, 0x45))

        val result = repository.uploadAndAnalyzeAudio(wavFile, "+919811112222")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().audioId).isEqualTo("aud-999")
        assertThat(result.getOrThrow().riskLevel).isEqualTo(RiskLevel.HIGH)
        assertThat(mockWebServer.requestCount).isEqualTo(2)
    }

    @Test
    fun `parses FastAPI structured error envelope cleanly`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody(
                    """
                    {
                      "error": {
                        "code": "HTTP_503",
                        "message": "Whisper model is unavailable on this host.",
                        "details": null
                      }
                    }
                    """.trimIndent()
                )
        )

        val result = repository.checkBackendHealth()
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Whisper model is unavailable")
    }

    @Test
    fun `analyzeAudioDirect completes in single HTTP request and caches analysis`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "analysis_id": "an-direct-1",
                      "transcript": "Send UPI verification payment immediately.",
                      "risk_score": 84,
                      "risk_level": "HIGH",
                      "detected_patterns": ["financial_pressure", "urgency"],
                      "recommendations": ["Do not transfer money."],
                      "is_heuristic": true,
                      "confidence_disclaimer": "Heuristic only.",
                      "audio_id": "aud-direct-1",
                      "created_at": "2026-11-11T10:02:00Z"
                    }
                    """.trimIndent()
                )
        )

        val wavFile = tempFolder.newFile("direct.wav")
        wavFile.writeBytes(ByteArray(128) { 0x52 })

        val result = repository.analyzeAudioDirect(wavFile, "+919876540001")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().audioId).isEqualTo("aud-direct-1")
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `persistAndSyncCallRecording falls back to audio upload when Whisper returns 422 silent speech`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody(
                    """
                    {
                      "error": {
                        "code": "HTTP_422",
                        "message": "The audio recording contains no detectable speech or is silent.",
                        "details": null
                      }
                    }
                    """.trimIndent()
                )
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """
                    {
                      "audio_id": "aud-silent-1",
                      "filename": "aud-silent-1.wav",
                      "original_filename": "call_silent.wav",
                      "file_size_bytes": 256,
                      "format": "wav",
                      "status": "uploaded",
                      "upload_time": "2026-11-11T10:03:00Z"
                    }
                    """.trimIndent()
                )
        )

        val wavFile = tempFolder.newFile("call_silent.wav")
        wavFile.writeBytes(ByteArray(256) { 0x01 })

        val saved = repository.persistAndSyncCallRecording(
            recordingFile = wavFile,
            durationSeconds = 5,
            sourcePhoneNumber = "9876500011"
        )

        assertThat(saved.syncStatus).isEqualTo(RecordingSyncStatus.UPLOADED_AUDIO_ONLY)
        assertThat(saved.audioId).isEqualTo("aud-silent-1")
        val storedList = repository.callRecordingsFlow.first()
        assertThat(storedList).hasSize(1)
        assertThat(storedList.first().recordingId).isEqualTo(saved.recordingId)
    }

    @Test
    fun `persistAndSyncCallRecording stores locally when offline and syncs on retry`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"code":"HTTP_500","message":"Offline"}}"""))
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "analysis_id": "an-retry-1",
                      "transcript": "KYC update link required immediately.",
                      "risk_score": 68,
                      "risk_level": "HIGH",
                      "detected_patterns": ["account_suspension"],
                      "recommendations": ["Do not click KYC links."],
                      "is_heuristic": true,
                      "confidence_disclaimer": "Heuristic only.",
                      "audio_id": "aud-retry-1",
                      "created_at": "2026-11-11T10:04:00Z"
                    }
                    """.trimIndent()
                )
        )

        val wavFile = tempFolder.newFile("call_retry.wav")
        wavFile.writeBytes(ByteArray(256) { 0x02 })

        val initial = repository.persistAndSyncCallRecording(
            recordingFile = wavFile,
            durationSeconds = 8,
            sourcePhoneNumber = "+919800011122"
        )
        assertThat(initial.syncStatus).isEqualTo(RecordingSyncStatus.LOCAL_PENDING)

        val synced = repository.retrySyncCallRecording(initial.recordingId)
        assertThat(synced?.syncStatus).isEqualTo(RecordingSyncStatus.SYNCED_ANALYSIS)
        assertThat(synced?.analysisId).isEqualTo("an-retry-1")
        assertThat(synced?.audioId).isEqualTo("aud-retry-1")
    }
}

