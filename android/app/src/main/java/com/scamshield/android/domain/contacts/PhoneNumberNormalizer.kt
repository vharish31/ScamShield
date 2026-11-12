package com.scamshield.android.domain.contacts

/**
 * Pure-Kotlin phone number normalizer designed for offline, on-device caller matching.
 * Supports E.164 prefixes (+91, +1, +44, etc.), 00-IDD prefixes, trunk leading '0',
 * formatted punctuation, SIP/tel URI prefixes, and Indian 10-digit mobile numbers.
 */
object PhoneNumberNormalizer {

    private val NON_PHONE_LABELS = setOf(
        "unknown", "private", "private number", "restricted", "anonymous",
        "withheld", "unavailable", "payphone", "blocked", "caller id withheld"
    )

    /**
     * Extracts canonical normalized lookup keys for a raw phone number or URI.
     * Multiple keys allow reliable matching between saved formats such as
     * "+91 98765 43210", "09876543210", and "9876543210" without causing false
     * positives on short codes.
     */
    fun extractLookupKeys(raw: String?): Set<String> {
        val cleaned = normalizeToCanonical(raw) ?: return emptySet()
        val keys = linkedSetOf(cleaned)
        val digitsOnly = cleaned.removePrefix("+")

        // Handle Indian country code (+91 or 91XXXXXXXXXX where 12 digits total and mobile starts 6..9)
        if (cleaned.startsWith("+91") && digitsOnly.length == 12) {
            val national = digitsOnly.substring(2)
            keys.add(national)
            keys.add("0$national")
        } else if (!cleaned.startsWith("+") && digitsOnly.length == 12 && digitsOnly.startsWith("91") &&
            digitsOnly[2] in '6'..'9'
        ) {
            val national = digitsOnly.substring(2)
            keys.add(national)
            keys.add("+91$national")
            keys.add("0$national")
        } else if (!cleaned.startsWith("+") && digitsOnly.length == 11 && digitsOnly.startsWith("0")) {
            val national = digitsOnly.substring(1)
            keys.add(national)
            if (national.length == 10 && national[0] in '6'..'9') {
                keys.add("+91$national")
            }
        } else if (!cleaned.startsWith("+") && digitsOnly.length == 10 && digitsOnly[0] in '6'..'9') {
            keys.add("+91$digitsOnly")
            keys.add("0$digitsOnly")
        }

        // Handle North America (+1 NXX NXX XXXX)
        if (cleaned.startsWith("+1") && digitsOnly.length == 11) {
            keys.add(digitsOnly.substring(1))
        } else if (!cleaned.startsWith("+") && digitsOnly.length == 11 && digitsOnly.startsWith("1")) {
            keys.add("+$digitsOnly")
            keys.add(digitsOnly.substring(1))
        }

        // Generic trunk '0' equivalence for international E.164 numbers (e.g. +44 7911... vs 07911...)
        if (cleaned.startsWith("+") && digitsOnly.length >= 11) {
            // Do not produce short substrings (<7 digits); match exact E.164 and digitsOnly
            keys.add(digitsOnly)
        }

        return keys
    }

    /**
     * Produces a single primary canonical representation suitable for display-safe storage
     * and equality checks. Returns null if the input is unavailable, withheld, or non-dialable.
     */
    fun normalizeToCanonical(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        var working = raw.trim()
        if (working.lowercase() in NON_PHONE_LABELS) return null

        // Handle SIP/tel schemes such as "tel:+919876543210;phone-context=..." or "sip:+91...@domain"
        if (working.startsWith("tel:", ignoreCase = true)) {
            working = working.substring(4)
        } else if (working.startsWith("sip:", ignoreCase = true)) {
            working = working.substring(4).substringBefore('@')
        }

        // Strip URI parameters and pause/extension markers (, ; #)
        working = working.substringBefore(';').substringBefore(',').substringBefore('#').trim()
        if (working.isEmpty() || working.lowercase() in NON_PHONE_LABELS) return null

        val hasPlus = working.startsWith("+")
        val digits = working.filter { it.isDigit() }
        if (digits.length < 3) return null

        // Convert international 00 prefix (e.g., 00919876543210 -> +919876543210)
        if (!hasPlus && digits.startsWith("00") && digits.length > 6) {
            return "+" + digits.substring(2)
        }

        // Canonicalize Indian 10-digit mobile numbers & 0-prefixed Indian mobiles to +91 when unambiguous
        return when {
            hasPlus -> "+$digits"
            digits.length == 10 && digits[0] in '6'..'9' -> "+91$digits"
            digits.length == 11 && digits.startsWith("0") && digits[1] in '6'..'9' ->
                "+91${digits.substring(1)}"
            digits.length == 12 && digits.startsWith("91") && digits[2] in '6'..'9' ->
                "+$digits"
            else -> digits
        }
    }

    fun isUnavailableNumber(raw: String?): Boolean = normalizeToCanonical(raw) == null

    fun maskNumberForPrivacy(raw: String?): String {
        val canonical = normalizeToCanonical(raw) ?: return "Private / Unknown"
        if (canonical.length <= 4) return "****"
        val prefixLen = if (canonical.startsWith("+")) 3 else 2
        val suffix = canonical.takeLast(4)
        val prefix = canonical.take(prefixLen.coerceAtMost(canonical.length - 4))
        return "$prefix••••$suffix"
    }
}

