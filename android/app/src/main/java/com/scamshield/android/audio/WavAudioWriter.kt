package com.scamshield.android.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Pure-Kotlin PCM-16 WAV writer producing standard RIFF/WAVE files accepted by the
 * FastAPI backend magic-byte validator (`RIFF....WAVE`) and Faster-Whisper STT.
 */
object WavAudioWriter {
    const val SAMPLE_RATE_HZ = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val WAV_HEADER_BYTES = 44

    fun buildWavHeader(pcmByteSize: Long, sampleRate: Int = SAMPLE_RATE_HZ): ByteArray {
        val totalDataLen = pcmByteSize + 36
        val byteRate = sampleRate * CHANNELS * (BITS_PER_SAMPLE / 8)
        val blockAlign = CHANNELS * (BITS_PER_SAMPLE / 8)

        return ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('R'.code.toByte())
            put('I'.code.toByte())
            put('F'.code.toByte())
            put('F'.code.toByte())
            putInt(totalDataLen.toInt())
            put('W'.code.toByte())
            put('A'.code.toByte())
            put('V'.code.toByte())
            put('E'.code.toByte())
            put('f'.code.toByte())
            put('m'.code.toByte())
            put('t'.code.toByte())
            put(' '.code.toByte())
            putInt(16) // PCM fmt subchunk size
            putShort(1.toShort()) // AudioFormat = 1 (PCM)
            putShort(CHANNELS.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(BITS_PER_SAMPLE.toShort())
            put('d'.code.toByte())
            put('a'.code.toByte())
            put('t'.code.toByte())
            put('a'.code.toByte())
            putInt(pcmByteSize.toInt())
        }.array()
    }

    fun writePcmShortsToWavFile(
        outputFile: File,
        samples: ShortArray,
        sampleCount: Int = samples.size,
        sampleRate: Int = SAMPLE_RATE_HZ
    ): File {
        val pcmBytes = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val s = samples[i].toInt()
            pcmBytes[i * 2] = (s and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((s ushr 8) and 0xFF).toByte()
        }
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            fos.write(buildWavHeader(pcmBytes.size.toLong(), sampleRate))
            fos.write(pcmBytes)
        }
        return outputFile
    }

    /**
     * Patches the RIFF/data length headers on an already-written incremental WAV file.
     */
    fun finalizeWavFileHeader(wavFile: File, sampleRate: Int = SAMPLE_RATE_HZ) {
        val fileLength = wavFile.length()
        if (fileLength < WAV_HEADER_BYTES) return
        val pcmBytes = fileLength - WAV_HEADER_BYTES
        RandomAccessFile(wavFile, "rw").use { raf ->
            raf.seek(0)
            raf.write(buildWavHeader(pcmBytes, sampleRate))
        }
    }

    /**
     * Normalized RMS signal level in [0f, 1f] calculated from genuine PCM samples.
     */
    fun computeNormalizedRms(samples: ShortArray, count: Int): Float {
        if (count <= 0) return 0f
        var sumSquares = 0.0
        for (i in 0 until count) {
            val normalized = samples[i] / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / count)
        return (rms * 3.5).coerceIn(0.0, 1.0).toFloat()
    }
}

