package com.scamshield.android.service

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.scamshield.android.MainActivity
import com.scamshield.android.R
import com.scamshield.android.audio.AudioMonitorController
import com.scamshield.android.audio.WavAudioWriter
import com.scamshield.android.data.repository.ScamDetectionRepository
import com.scamshield.android.notifications.ScamNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Mode B: User-Authorized Foreground Service for Live Audio Scam Analysis.
 *
 * Captures 16kHz PCM audio continuously to a session WAV file while concurrently
 * streaming 4-second audible chunks over an async upload worker channel so
 * microphone capture never stalls while awaiting AI transcription.
 */
@AndroidEntryPoint
class LiveAudioAnalysisService : Service() {

    companion object {
        const val ACTION_START = "com.scamshield.android.action.START_LIVE_AUDIO"
        const val ACTION_STOP = "com.scamshield.android.action.STOP_LIVE_AUDIO"
        const val ACTION_ANALYZE_NOW = "com.scamshield.android.action.ANALYZE_BUFFER_NOW"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"

        private const val NOTIFICATION_ID = 4101
        private const val CHUNK_DURATION_MILLIS = 4_000L
        private const val MIN_FLUSH_MILLIS = 1_500L
        private const val AUDIBLE_RMS_THRESHOLD = 0.01f

        fun startIntent(context: Context, phoneNumber: String? = null): Intent =
            Intent(context, LiveAudioAnalysisService::class.java).apply {
                action = ACTION_START
                if (!phoneNumber.isNullOrBlank()) {
                    putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                }
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, LiveAudioAnalysisService::class.java).apply {
                action = ACTION_STOP
            }

        fun analyzeNowIntent(context: Context): Intent =
            Intent(context, LiveAudioAnalysisService::class.java).apply {
                action = ACTION_ANALYZE_NOW
            }
    }

    @Inject lateinit var audioMonitorController: AudioMonitorController
    @Inject lateinit var detectionRepository: ScamDetectionRepository
    @Inject lateinit var notificationManager: ScamNotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    @Volatile private var stopRequested = false
    @Volatile private var forceFlushRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            audioMonitorController.stopSignal.collect {
                requestGracefulStop()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> requestGracefulStop()
            ACTION_ANALYZE_NOW -> forceFlushRequested = true
            ACTION_START -> {
                val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER).orEmpty()
                if (phone.isNotBlank()) {
                    audioMonitorController.setTargetPhoneNumber(phone)
                }
                startCapture()
            }
            else -> requestGracefulStop()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            audioMonitorController.onChunkError("Microphone permission is required for live audio analysis.")
            stopSelf()
            return
        }

        if (captureJob?.isActive == true) return

        stopRequested = false
        forceFlushRequested = false

        notificationManager.ensureNotificationChannels()
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            val phone = audioMonitorController.state.value.targetPhoneNumber.takeIf { it.isNotBlank() }
            audioMonitorController.onChunkError(
                "Android requires opening the notification or app to start microphone monitoring during calls."
            )
            notificationManager.notifyCallConnectedStartPrompt(phone)
            stopSelf()
            return
        }

        notificationManager.cancelCallConnectedPrompt()
        captureJob = serviceScope.launch {
            runCaptureLoop()
        }
    }

    private fun requestGracefulStop() {
        if (captureJob?.isActive == true) {
            stopRequested = true
            audioMonitorController.onFinalizingRecording()
        } else {
            audioMonitorController.onMonitoringStopped("Live microphone monitoring stopped.")
            stopForegroundAndSelf()
        }
    }

    private suspend fun runCaptureLoop() {
        val sampleRate = WavAudioWriter.SAMPLE_RATE_HZ
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBufBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
            .coerceAtLeast(sampleRate)

        val audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION
        var audioRecord = createAudioRecord(audioSource, sampleRate, channelConfig, encoding, minBufBytes)
        var sourceLabel = "Device Microphone (Voice Communication — turn Speakerphone ON for callers)"

        if (audioRecord == null) {
            audioRecord = createAudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                minBufBytes
            )
            sourceLabel = "Device Microphone (Standard MIC — turn Speakerphone ON for callers)"
        }

        if (audioRecord == null) {
            audioMonitorController.onChunkError("Unable to initialize microphone AudioRecord on this device.")
            audioMonitorController.onMonitoringStopped("Microphone capture unavailable.")
            stopForegroundAndSelf()
            return
        }

        val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
        val sessionRecordingFile = File(recordingsDir, "call_${System.currentTimeMillis()}.wav")
        val sessionOutput = BufferedOutputStream(FileOutputStream(sessionRecordingFile))
        sessionOutput.write(WavAudioWriter.buildWavHeader(0L))

        val liveChunkChannel = Channel<ByteArray>(capacity = Channel.UNLIMITED)
        val uploaderJob = serviceScope.launch {
            for (pcmChunk in liveChunkChannel) {
                if (stopRequested) continue
                analyzeRollingChunk(pcmChunk)
            }
        }

        audioMonitorController.onMonitoringStarted(sourceLabel)

        val pcmShortBuffer = ShortArray(sampleRate / 5) // 200ms frames
        val chunkStream = ByteArrayOutputStream()
        val startTimeMillis = System.currentTimeMillis()
        var chunkStartMillis = startTimeMillis
        var chunkPeakAmplitude = 0f
        var totalElapsedSec = 0

        try {
            audioRecord.startRecording()
            while (!stopRequested) {
                val readCount = audioRecord.read(pcmShortBuffer, 0, pcmShortBuffer.size)
                if (readCount > 0) {
                    val amplitude = WavAudioWriter.computeNormalizedRms(pcmShortBuffer, readCount)
                    if (amplitude > chunkPeakAmplitude) {
                        chunkPeakAmplitude = amplitude
                    }

                    val frameBytes = ByteArray(readCount * 2)
                    for (i in 0 until readCount) {
                        val s = pcmShortBuffer[i].toInt()
                        frameBytes[i * 2] = (s and 0xFF).toByte()
                        frameBytes[i * 2 + 1] = ((s ushr 8) and 0xFF).toByte()
                    }
                    sessionOutput.write(frameBytes)
                    chunkStream.write(frameBytes)

                    val now = System.currentTimeMillis()
                    totalElapsedSec = ((now - startTimeMillis) / 1000L).toInt()
                    audioMonitorController.onTick(totalElapsedSec, amplitude)

                    val chunkElapsedMillis = now - chunkStartMillis
                    val shouldFlush = (chunkElapsedMillis >= CHUNK_DURATION_MILLIS && chunkPeakAmplitude >= AUDIBLE_RMS_THRESHOLD) ||
                        (forceFlushRequested && chunkElapsedMillis >= MIN_FLUSH_MILLIS)

                    if (shouldFlush) {
                        forceFlushRequested = false
                        val pcmCopy = chunkStream.toByteArray()
                        chunkStream.reset()
                        chunkStartMillis = now
                        chunkPeakAmplitude = 0f
                        if (pcmCopy.size >= sampleRate) {
                            liveChunkChannel.trySend(pcmCopy)
                        }
                    } else if (chunkElapsedMillis >= CHUNK_DURATION_MILLIS) {
                        // Discard empty silence chunks from live queue (full recording still retains continuous audio)
                        chunkStream.reset()
                        chunkStartMillis = now
                        chunkPeakAmplitude = 0f
                    }
                }
            }
        } catch (e: Exception) {
            audioMonitorController.onChunkError("Capture interrupted: ${e.localizedMessage ?: "Audio error"}")
        } finally {
            withContext(NonCancellable) {
                runCatching { audioRecord.stop() }
                runCatching { audioRecord.release() }
                runCatching {
                    sessionOutput.flush()
                    sessionOutput.close()
                    WavAudioWriter.finalizeWavFileHeader(sessionRecordingFile)
                }

                // Close live chunk channel and allow any single in-flight live chunk a brief window to finish
                liveChunkChannel.close()
                withTimeoutOrNull(4_000L) { uploaderJob.join() }
                uploaderJob.cancel()

                // Guarantee the complete recorded call is stored in Room & uploaded to FastAPI/Firestore
                val targetPhone = audioMonitorController.state.value.targetPhoneNumber.takeIf { it.isNotBlank() }
                val latestLive = audioMonitorController.state.value.latestAnalysis
                val savedRecording = detectionRepository.persistAndSyncCallRecording(
                    recordingFile = sessionRecordingFile,
                    durationSeconds = totalElapsedSec.coerceAtLeast(1),
                    sourcePhoneNumber = targetPhone,
                    latestLiveAnalysis = latestLive
                )
                audioMonitorController.onRecordingPersisted(savedRecording)
                stopForegroundAndSelf()
            }
        }
    }

    private suspend fun analyzeRollingChunk(pcmBytes: ByteArray) {
        audioMonitorController.onChunkUploadStarted()
        val cacheDir = File(cacheDir, "live_audio_chunks").apply { mkdirs() }
        val chunkFile = File(cacheDir, "chunk_${System.currentTimeMillis()}.wav")
        try {
            chunkFile.outputStream().use { fos ->
                fos.write(WavAudioWriter.buildWavHeader(pcmBytes.size.toLong()))
                fos.write(pcmBytes)
            }

            val targetPhone = audioMonitorController.state.value.targetPhoneNumber.takeIf { it.isNotBlank() }
            val result = detectionRepository.analyzeAudioDirect(chunkFile, sourcePhoneNumber = targetPhone)
            result.onSuccess { analysis ->
                audioMonitorController.onChunkAnalyzed(analysis)
                if (analysis.riskLevel.name != "LOW") {
                    notificationManager.notifyAnalysisResult(analysis)
                }
            }.onFailure { err ->
                val message = err.localizedMessage ?: "Failed to analyze chunk."
                if (message.contains("no detectable speech", ignoreCase = true) ||
                    message.contains("silent", ignoreCase = true)
                ) {
                    audioMonitorController.onSilentChunkSkipped("Listening for clear caller speech...")
                } else {
                    audioMonitorController.onChunkError(message)
                }
            }
        } finally {
            runCatching { chunkFile.delete() }
        }
    }

    @Suppress("MissingPermission")
    private fun createAudioRecord(
        source: Int,
        sampleRate: Int,
        channelConfig: Int,
        encoding: Int,
        bufferBytes: Int
    ): AudioRecord? {
        return try {
            val record = AudioRecord(source, sampleRate, channelConfig, encoding, bufferBytes * 2)
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                record
            } else {
                record.release()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun buildForegroundNotification() = NotificationCompat.Builder(
        this,
        ScamNotificationManager.CHANNEL_LIVE_SERVICE
    ).apply {
        val openIntent = PendingIntent.getActivity(
            this@LiveAudioAnalysisService,
            10,
            Intent(this@LiveAudioAnalysisService, MainActivity::class.java).apply {
                putExtra(ScamNotificationManager.EXTRA_NAV_DESTINATION, "live")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this@LiveAudioAnalysisService,
            11,
            stopIntent(this@LiveAudioAnalysisService),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setSmallIcon(R.drawable.ic_launcher_foreground)
        setContentTitle("Scam Shield Live Audio Monitoring Active")
        setContentText("Recording & analyzing live microphone audio. Tap Stop to save & end.")
        setOngoing(true)
        setContentIntent(openIntent)
        addAction(0, "Stop & Save", stopPendingIntent)
        setPriority(NotificationCompat.PRIORITY_LOW)
    }.build()

    override fun onDestroy() {
        stopRequested = true
        captureJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
