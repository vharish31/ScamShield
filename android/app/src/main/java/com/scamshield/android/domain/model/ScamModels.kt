package com.scamshield.android.domain.model

enum class RiskLevel(
    val apiValue: String,
    val label: String,
    val warningMessage: String
) {
    LOW(
        apiValue = "LOW",
        label = "Low Risk",
        warningMessage = "No major scam indicators detected. Stay cautious."
    ),
    MEDIUM(
        apiValue = "MEDIUM",
        label = "Medium Risk",
        warningMessage = "Warning: Some suspicious communication patterns were identified."
    ),
    HIGH(
        apiValue = "HIGH",
        label = "High Risk",
        warningMessage = "Potential scam detected. Do not share OTPs, PINs or banking information."
    ),
    CRITICAL(
        apiValue = "CRITICAL",
        label = "Critical Risk",
        warningMessage = "Critical scam warning! Stop sharing information and do not transfer money."
    );

    companion object {
        fun fromApiValue(value: String?): RiskLevel {
            val normalized = value?.trim()?.uppercase()
            return entries.firstOrNull { it.apiValue == normalized } ?: LOW
        }
    }
}

enum class ScamCategory(
    val apiKey: String,
    val displayName: String,
    val shortExplanation: String
) {
    AUTHORITY_IMPERSONATION(
        apiKey = "authority_impersonation",
        displayName = "Authority Impersonation",
        shortExplanation = "Claims to represent police, government, bank, or regulatory officials."
    ),
    THREATS_INTIMIDATION(
        apiKey = "threats_intimidation",
        displayName = "Threats & Intimidation",
        shortExplanation = "Uses arrest threats, warrants, or legal penalties to force compliance."
    ),
    URGENCY(
        apiKey = "urgency",
        displayName = "Urgency & Pressure",
        shortExplanation = "Demands immediate action without time to verify independently."
    ),
    CREDENTIAL_HARVESTING(
        apiKey = "credential_harvesting",
        displayName = "Credential Harvesting",
        shortExplanation = "Requests OTPs, PINs, CVV, passwords, or Aadhaar/PAN details."
    ),
    ACCOUNT_SUSPENSION(
        apiKey = "account_suspension",
        displayName = "Account Suspension Fraud",
        shortExplanation = "Claims a bank account, KYC, SIM, or card is about to be blocked."
    ),
    SECRECY_ISOLATION(
        apiKey = "secrecy_isolation",
        displayName = "Secrecy & Isolation",
        shortExplanation = "Discourages consulting family or friends or instructs staying on call."
    ),
    FEAR_MANIPULATION(
        apiKey = "fear_manipulation",
        displayName = "Fear Manipulation",
        shortExplanation = "Uses fabricated crises such as illegal parcels or money laundering claims."
    ),
    FINANCIAL_PRESSURE(
        apiKey = "financial_pressure",
        displayName = "Financial Pressure",
        shortExplanation = "Pushes transfers to 'safe' accounts, UPI IDs, gift cards, or crypto."
    );

    companion object {
        fun fromApiKey(key: String): ScamCategory? =
            entries.firstOrNull { it.apiKey.equals(key.trim(), ignoreCase = true) }

        fun displayLabelFor(key: String): String =
            fromApiKey(key)?.displayName
                ?: key.replace('_', ' ')
                    .split(' ')
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { part ->
                        part.replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                        }
                    }
    }
}

enum class CallerTrustStatus {
    TRUSTED,
    UNKNOWN,
    UNAVAILABLE_NUMBER
}

enum class AlertType(val channelId: String, val titlePrefix: String) {
    UNKNOWN_CALLER("unknown_caller_alerts", "Unknown Caller"),
    SUSPICIOUS_CALLER("suspicious_caller_alerts", "Suspicious Caller"),
    HIGH_RISK_CALLER("high_risk_alerts", "High-Risk Caller"),
    CRITICAL_SCAM("critical_scam_alerts", "Critical Scam Warning")
}

data class TrustedContact(
    val id: String,
    val displayName: String,
    val rawNumber: String,
    val normalizedNumber: String
)

data class AnalysisResult(
    val analysisId: String,
    val transcript: String,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val detectedPatterns: List<String>,
    val recommendations: List<String>,
    val isHeuristic: Boolean,
    val confidenceDisclaimer: String,
    val audioId: String?,
    val createdAt: String,
    val sourcePhoneNumber: String? = null
)

data class HistorySummary(
    val analysisId: String,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val detectedPatterns: List<String>,
    val transcriptPreview: String,
    val audioId: String?,
    val createdAt: String
)

data class ScreeningEvent(
    val id: Long = 0,
    val normalizedNumber: String,
    val displayLabel: String,
    val trustStatus: CallerTrustStatus,
    val contactsPermissionGranted: Boolean,
    val wasPriorFlagged: Boolean,
    val priorRiskLevel: RiskLevel?,
    val timestampMillis: Long,
    val notes: String
)

data class AppNotificationItem(
    val id: Long = 0,
    val alertType: AlertType,
    val title: String,
    val message: String,
    val numberMasked: String?,
    val riskLevel: RiskLevel?,
    val analysisId: String?,
    val dedupKey: String,
    val timestampMillis: Long,
    val isRead: Boolean = false
)

data class UserAccount(
    val uid: String,
    val email: String?,
    val displayName: String?
)

data class BackendHealth(
    val status: String,
    val version: String,
    val firebaseConnected: Boolean,
    val whisperConfigured: Boolean,
    val mlModelStatus: String
)

enum class RecordingSyncStatus {
    SYNCED_ANALYSIS,
    UPLOADED_AUDIO_ONLY,
    LOCAL_PENDING,
    FAILED
}

data class CallRecording(
    val recordingId: String,
    val localFilePath: String,
    val numberMasked: String?,
    val durationSeconds: Int,
    val fileSizeBytes: Long,
    val audioId: String?,
    val analysisId: String?,
    val riskLevel: RiskLevel?,
    val riskScore: Int?,
    val transcriptSummary: String,
    val syncStatus: RecordingSyncStatus,
    val errorNote: String?,
    val timestampMillis: Long
)

