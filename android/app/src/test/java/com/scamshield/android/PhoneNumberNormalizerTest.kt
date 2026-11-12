package com.scamshield.android.domain.contacts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneNumberNormalizerTest {

    @Test
    fun `matches Indian mobile variants cleanly`() {
        val savedKeys = PhoneNumberNormalizer.extractLookupKeys("+91 98765-43210")

        val incomingPlain = PhoneNumberNormalizer.extractLookupKeys("9876543210")
        val incomingZeroPrefix = PhoneNumberNormalizer.extractLookupKeys("09876543210")
        val incomingDoubleZero = PhoneNumberNormalizer.extractLookupKeys("00919876543210")
        val incomingTelUri = PhoneNumberNormalizer.extractLookupKeys("tel:+919876543210;phone-context=ind")
        val incomingSipUri = PhoneNumberNormalizer.extractLookupKeys("sip:+919876543210@carrier.example")

        assertThat(savedKeys.intersect(incomingPlain)).isNotEmpty()
        assertThat(savedKeys.intersect(incomingZeroPrefix)).isNotEmpty()
        assertThat(savedKeys.intersect(incomingDoubleZero)).isNotEmpty()
        assertThat(savedKeys.intersect(incomingTelUri)).isNotEmpty()
        assertThat(savedKeys.intersect(incomingSipUri)).isNotEmpty()

        assertThat(PhoneNumberNormalizer.normalizeToCanonical("9876543210")).isEqualTo("+919876543210")
        assertThat(PhoneNumberNormalizer.normalizeToCanonical("09876543210")).isEqualTo("+919876543210")
    }

    @Test
    fun `matches North American number formats without colliding on short codes`() {
        val usKeys = PhoneNumberNormalizer.extractLookupKeys("+1 (415) 555-2671")
        val incomingUs = PhoneNumberNormalizer.extractLookupKeys("14155552671")
        val shortEmergency = PhoneNumberNormalizer.extractLookupKeys("1930")

        assertThat(usKeys.intersect(incomingUs)).isNotEmpty()
        assertThat(usKeys.intersect(shortEmergency)).isEmpty()
    }

    @Test
    fun `handles unavailable restricted and blank caller IDs safely`() {
        listOf(null, "", "   ", "Unknown", "Restricted", "Private Number", "anonymous").forEach { raw ->
            assertThat(PhoneNumberNormalizer.normalizeToCanonical(raw)).isNull()
            assertThat(PhoneNumberNormalizer.extractLookupKeys(raw)).isEmpty()
            assertThat(PhoneNumberNormalizer.isUnavailableNumber(raw)).isTrue()
            assertThat(PhoneNumberNormalizer.maskNumberForPrivacy(raw)).isEqualTo("Private / Unknown")
        }
    }

    @Test
    fun `masks valid numbers preserving only prefix and last four digits`() {
        val masked = PhoneNumberNormalizer.maskNumberForPrivacy("+919876543210")
        assertThat(masked).isEqualTo("+91••••3210")
    }
}

