package com.scamshield.android.service

import com.scamshield.android.data.contacts.ContactsRepository
import com.scamshield.android.data.local.ScreeningEventDao
import com.scamshield.android.data.local.ScreeningEventEntity
import com.scamshield.android.data.preferences.AppPreferencesRepository
import com.scamshield.android.data.repository.ScamDetectionRepository
import com.scamshield.android.domain.contacts.PhoneNumberNormalizer
import com.scamshield.android.domain.model.CallerTrustStatus
import com.scamshield.android.domain.model.RiskLevel
import com.scamshield.android.notifications.ScamNotificationManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CallScreeningDecision {
    data object ScreeningDisabled : CallScreeningDecision
    data class TrustedContactBypassed(val contactName: String) : CallScreeningDecision
    data class UnknownCallerScreened(
        val trustStatus: CallerTrustStatus,
        val canonicalNumber: String?,
        val contactsPermissionGranted: Boolean,
        val priorRiskLevel: RiskLevel?
    ) : CallScreeningDecision
}

/**
 * Pure coordination logic for incoming call screening.
 * Enforces local trusted-contact exclusion, zero auto-blocking by default,
 * zero uploading of contacts or call metadata, and honest disclosure of screening scope.
 */
@Singleton
class CallScreeningProcessor @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
    private val contactsRepository: ContactsRepository,
    private val detectionRepository: ScamDetectionRepository,
    private val screeningEventDao: ScreeningEventDao,
    private val notificationManager: ScamNotificationManager,
    private val callSessionTracker: CallSessionTracker
) {
    suspend fun screenIncomingCall(
        rawNumber: String?,
        isRestrictedOrPrivatePresentation: Boolean
    ): CallScreeningDecision {
        val settings = preferencesRepository.settingsFlow.first()
        if (!settings.liveScreeningEnabled) {
            callSessionTracker.onIncomingCallScreened(CallScreeningDecision.ScreeningDisabled)
            return CallScreeningDecision.ScreeningDisabled
        }

        val inputNumber = if (isRestrictedOrPrivatePresentation) null else rawNumber
        val classification = contactsRepository.classifyCaller(inputNumber)

        // Rule: Trusted saved mobile contacts are NEVER monitored, analyzed, or alerted.
        if (classification.trustStatus == CallerTrustStatus.TRUSTED) {
            val trustedDecision = CallScreeningDecision.TrustedContactBypassed(
                contactName = classification.matchedContactName ?: "Saved Contact"
            )
            callSessionTracker.onIncomingCallScreened(trustedDecision)
            return trustedDecision
        }

        // Check only local device analysis history for previously analyzed suspicious calls from this number.
        val canonical = classification.canonicalNumber
        val priorAnalysis = canonical?.let { detectionRepository.findPriorAnalysisForNumber(it) }
        val priorFlaggedRisk = priorAnalysis?.riskLevel?.takeIf {
            it == RiskLevel.HIGH || it == RiskLevel.CRITICAL || it == RiskLevel.MEDIUM
        }

        val displayLabel = PhoneNumberNormalizer.maskNumberForPrivacy(canonical)
        val notes = buildString {
            append(
                if (classification.trustStatus == CallerTrustStatus.UNAVAILABLE_NUMBER) {
                    "Caller ID unavailable or restricted."
                } else {
                    "Number not found in saved contacts."
                }
            )
            if (!classification.contactsPermissionGranted) {
                append(" Contacts permission denied; saved contact exclusion cannot be guaranteed.")
            }
            if (priorFlaggedRisk != null) {
                append(" Previously flagged ${priorFlaggedRisk.apiValue} on this device.")
            }
        }

        screeningEventDao.insert(
            ScreeningEventEntity(
                normalizedNumber = canonical ?: "unavailable",
                displayLabel = displayLabel,
                trustStatus = classification.trustStatus.name,
                contactsPermissionGranted = classification.contactsPermissionGranted,
                wasPriorFlagged = priorFlaggedRisk != null,
                priorRiskLevel = priorFlaggedRisk?.apiValue,
                timestampMillis = System.currentTimeMillis(),
                notes = notes
            )
        )

        notificationManager.notifyUnknownCaller(
            canonicalNumber = canonical,
            contactsPermissionGranted = classification.contactsPermissionGranted,
            priorFlaggedRisk = priorFlaggedRisk
        )

        val decision = CallScreeningDecision.UnknownCallerScreened(
            trustStatus = classification.trustStatus,
            canonicalNumber = canonical,
            contactsPermissionGranted = classification.contactsPermissionGranted,
            priorRiskLevel = priorFlaggedRisk
        )
        callSessionTracker.onIncomingCallScreened(decision)
        return decision
    }
}

