package com.scamshield.android

import com.google.common.truth.Truth.assertThat
import com.scamshield.android.audio.WavAudioWriter
import com.scamshield.android.domain.model.RiskLevel
import com.scamshield.android.domain.model.ScamCategory
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScamModelsAndWavTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `risk level maps backend values and falls back safely`() {
        assertThat(RiskLevel.fromApiValue("CRITICAL")).isEqualTo(RiskLevel.CRITICAL)
        assertThat(RiskLevel.fromApiValue(" high ")).isEqualTo(RiskLevel.HIGH)
        assertThat(RiskLevel.fromApiValue("medium")).isEqualTo(RiskLevel.MEDIUM)
        assertThat(RiskLevel.fromApiValue("LOW")).isEqualTo(RiskLevel.LOW)
        assertThat(RiskLevel.fromApiValue("unexpected")).isEqualTo(RiskLevel.LOW)

        assertThat(RiskLevel.CRITICAL.warningMessage).contains("do not transfer money")
        assertThat(RiskLevel.HIGH.warningMessage).contains("OTPs")
    }

    @Test
    fun `all eight scam categories format cleanly plus unknown keys`() {
        val backendKeys = listOf(
            "authority_impersonation",
            "threats_intimidation",
            "urgency",
            "credential_harvesting",
            "account_suspension",
            "secrecy_isolation",
            "fear_manipulation",
            "financial_pressure"
        )
        backendKeys.forEach { key ->
            assertThat(ScamCategory.fromApiKey(key)).isNotNull()
        }
        assertThat(ScamCategory.displayLabelFor("new_emerging_tactic")).isEqualTo("New Emerging Tactic")
    }

    @Test
    fun `wav writer emits valid RIFF WAVE header and computes real rms`() {
        val samples = ShortArray(1600) { idx ->
            if (idx % 2 == 0) 4000 else -4000
        }
        val outFile = tempFolder.newFile("sample.wav")
        WavAudioWriter.writePcmShortsToWavFile(outFile, samples)

        val bytes = outFile.readBytes()
        assertThat(bytes.size).isEqualTo(WavAudioWriter.WAV_HEADER_BYTES + samples.size * 2)
        assertThat(String(bytes.sliceArray(0..3), Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(String(bytes.sliceArray(8..11), Charsets.US_ASCII)).isEqualTo("WAVE")

        val rmsLoud = WavAudioWriter.computeNormalizedRms(samples, samples.size)
        val silence = ShortArray(100) { 0 }
        val rmsSilent = WavAudioWriter.computeNormalizedRms(silence, silence.size)

        assertThat(rmsLoud).isGreaterThan(0.1f)
        assertThat(rmsSilent).isEqualTo(0f)
    }
}

