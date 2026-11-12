package com.scamshield.android.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.scamshield.android.MainActivity
import com.scamshield.android.R
import com.scamshield.android.data.local.NotificationLogDao
import com.scamshield.android.data.local.NotificationLogEntity
import com.scamshield.android.data.preferences.AppPreferencesRepository
import com.scamshield.android.domain.contacts.PhoneNumberNormalizer
import com.scamshield.android.domain.model.AlertType
import com.scamshield.android.domain.model.AnalysisResult
import com.scamshield.android.domain.model.AppNotificationItem
import com.scamshield.android.domain.model.RiskLevel
import com.scamshield.android.domain.model.ScamCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScamNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationLogDao: NotificationLogDao,
    private val preferencesRepository: AppPreferencesRepository
) {
    companion object {
        const val CHANNEL_UNKNOWN = "unknown_caller_alerts"
        const val CHANNEL_SUSPICIOUS = "suspicious_caller_alerts"
        const val CHANNEL_HIGH_RISK = "high_risk_alerts"
        const val CHANNEL_CRITICAL = "critical_scam_alerts"
        const val CHANNEL_LIVE_SERVICE = "live_monitoring_service"

        const val EXTRA_NAV_DESTINATION = "nav_destination"
        const val EXTRA_ANALYSIS_ID = "analysis_id"
        const val EXTRA_AUTO_START_LIVE = "auto_start_live"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val DEDUP_WINDOW_MILLIS = 60_000L
        private const val NOTIFICATION_ID_CALL_CONNECT_PROMPT = 4102
    }

    val notificationsFlow: Flow<List<AppNotificationItem>> =
        notificationLogDao.observeAll().map { list ->
            list.map { entity ->
                AppNotificationItem(
                    id = entity.id,
                    alertType = runCatching { AlertType.valueOf(entity.alertType) }
                        .getOrDefault(AlertType.UNKNOWN_CALLER),
                    title = entity.title,
                    message = entity.message,
                    numberMasked = entity.numberMasked,
                    riskLevel = entity.riskLevel?.let { RiskLevel.fromApiValue(it) },
                    analysisId = entity.analysisId,
                    dedupKey = entity.dedupKey,
                    timestampMillis = entity.timestampMillis,
                    isRead = entity.isRead
                )
            }
        }

    val unreadCountFlow: Flow<Int> = notificationLogDao.observeUnreadCount()

    fun ensureNotificationChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(
                CHANNEL_UNKNOWN,
                "Unknown Caller Screening",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when an unsaved/unknown caller is screened locally."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_SUSPICIOUS,
                "Suspicious Activity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Medium-risk indicators found during AI analysis."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_HIGH_RISK,
                "High-Risk Scam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Strong scam indicators detected in analyzed speech or text."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_CRITICAL,
                "Critical Scam Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts for critical scam indicators."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_LIVE_SERVICE,
                "Live Audio Analysis Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent indicator while microphone live analysis is active."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannels(channels)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    suspend fun notifyUnknownCaller(
        canonicalNumber: String?,
        contactsPermissionGranted: Boolean,
        priorFlaggedRisk: RiskLevel?
    ): Boolean = withContext(Dispatchers.IO) {
        val masked = PhoneNumberNormalizer.maskNumberForPrivacy(canonicalNumber)
        val dedupKey = "unknown:${canonicalNumber ?: "unavailable"}:${priorFlaggedRisk?.apiValue ?: "none"}"

        val (alertType, title, body) = when (priorFlaggedRisk) {
            RiskLevel.CRITICAL -> Triple(
                AlertType.CRITICAL_SCAM,
                "Critical Warning: Previously Flagged Number",
                "$masked was previously flagged in local analysis. Speech on this call is not analyzed automatically."
            )
            RiskLevel.HIGH -> Triple(
                AlertType.HIGH_RISK_CALLER,
                "High-Risk Caller Warning",
                "$masked was previously flagged in local analysis. Be careful before sharing personal info."
            )
            else -> Triple(
                AlertType.UNKNOWN_CALLER,
                "Unknown Caller Detected",
                buildString {
                    append("$masked is not in saved contacts. Local screening active.")
                    if (!contactsPermissionGranted) {
                        append(" Contacts access is off; saved-contact exclusion cannot be guaranteed.")
                    }
                }
            )
        }

        dispatchAlertIfNotDuplicate(
            alertType = alertType,
            title = title,
            body = body,
            publicFallbackText = "Scam Shield screened an incoming unknown call. Open app for details.",
            numberMasked = masked,
            riskLevel = priorFlaggedRisk,
            analysisId = null,
            dedupKey = dedupKey
        )
    }

    suspend fun notifyAnalysisResult(result: AnalysisResult): Boolean = withContext(Dispatchers.IO) {
        if (result.riskLevel == RiskLevel.LOW) return@withContext false

        val alertType = when (result.riskLevel) {
            RiskLevel.MEDIUM -> AlertType.SUSPICIOUS_CALLER
            RiskLevel.HIGH -> AlertType.HIGH_RISK_CALLER
            RiskLevel.CRITICAL -> AlertType.CRITICAL_SCAM
            RiskLevel.LOW -> return@withContext false
        }
        val topPatterns = result.detectedPatterns
            .take(2)
            .joinToString(", ") { ScamCategory.displayLabelFor(it) }

        val title = when (result.riskLevel) {
            RiskLevel.CRITICAL -> "Critical Scam Warning (${result.riskScore}/100)"
            RiskLevel.HIGH -> "High-Risk Caller (${result.riskScore}/100)"
            RiskLevel.MEDIUM -> "Suspicious Patterns (${result.riskScore}/100)"
            RiskLevel.LOW -> "Analysis Complete"
        }

        val body = buildString {
            append(result.riskLevel.warningMessage)
            if (topPatterns.isNotBlank()) {
                append(" Indicators: $topPatterns.")
            }
        }

        val masked = result.sourcePhoneNumber?.let { PhoneNumberNormalizer.maskNumberForPrivacy(it) }
        val dedupKey = "analysis:${result.analysisId}"

        dispatchAlertIfNotDuplicate(
            alertType = alertType,
            title = title,
            body = body,
            publicFallbackText = "Scam Shield detected potential scam indicators. Open app to review safely.",
            numberMasked = masked,
            riskLevel = result.riskLevel,
            analysisId = result.analysisId,
            dedupKey = dedupKey
        )
    }

    suspend fun markAllRead(): Unit = withContext(Dispatchers.IO) {
        notificationLogDao.markAllRead()
    }

    suspend fun clearAllLogs(): Unit = withContext(Dispatchers.IO) {
        notificationLogDao.clearAll()
    }

    private suspend fun dispatchAlertIfNotDuplicate(
        alertType: AlertType,
        title: String,
        body: String,
        publicFallbackText: String,
        numberMasked: String?,
        riskLevel: RiskLevel?,
        analysisId: String?,
        dedupKey: String
    ): Boolean {
        val now = System.currentTimeMillis()
        val recentCount = notificationLogDao.countRecentByDedupKey(
            dedupKey = dedupKey,
            sinceMillis = now - DEDUP_WINDOW_MILLIS
        )
        if (recentCount > 0) {
            return false
        }

        val rowId = notificationLogDao.insert(
            NotificationLogEntity(
                alertType = alertType.name,
                title = title,
                message = body,
                numberMasked = numberMasked,
                riskLevel = riskLevel?.apiValue,
                analysisId = analysisId,
                dedupKey = dedupKey,
                timestampMillis = now,
                isRead = false
            )
        )

        ensureNotificationChannels()
        if (!hasNotificationPermission()) {
            return true
        }

        val settings = preferencesRepository.settingsFlow.first()
        val channelId = when (alertType) {
            AlertType.UNKNOWN_CALLER -> CHANNEL_UNKNOWN
            AlertType.SUSPICIOUS_CALLER -> CHANNEL_SUSPICIOUS
            AlertType.HIGH_RISK_CALLER -> CHANNEL_HIGH_RISK
            AlertType.CRITICAL_SCAM -> CHANNEL_CRITICAL
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_DESTINATION, if (analysisId != null) "analysis" else "live")
            if (analysisId != null) {
                putExtra(EXTRA_ANALYSIS_ID, analysisId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            rowId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = when (alertType) {
            AlertType.CRITICAL_SCAM, AlertType.HIGH_RISK_CALLER, AlertType.UNKNOWN_CALLER ->
                NotificationCompat.PRIORITY_HIGH
            AlertType.SUSPICIOUS_CALLER -> NotificationCompat.PRIORITY_DEFAULT
        }

        val publicVersion = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Scam Shield Security Alert")
            .setContentText(publicFallbackText)
            .setPriority(priority)
            .build()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (settings.lockScreenPrivacyEnabled) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            builder.setPublicVersion(publicVersion)
        } else {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        NotificationManagerCompat.from(context).notify(rowId.toInt() + 1000, builder.build())
        return true
    }

    fun notifyCallConnectedStartPrompt(canonicalNumber: String?) {
        ensureNotificationChannels()
        if (!hasNotificationPermission()) return

        val masked = canonicalNumber?.let { PhoneNumberNormalizer.maskNumberForPrivacy(it) }
            ?: "Unknown caller"

        val startActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_DESTINATION, "live")
            putExtra(EXTRA_AUTO_START_LIVE, true)
            if (!canonicalNumber.isNullOrBlank()) {
                putExtra(EXTRA_PHONE_NUMBER, canonicalNumber)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_CALL_CONNECT_PROMPT,
            startActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_UNKNOWN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Unknown Call Connected — Tap to Analyze")
            .setContentText("Tap to start live microphone analysis for $masked (Android requires foreground confirmation for mic).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Start Live Analysis", pendingIntent)

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_CALL_CONNECT_PROMPT, builder.build())
    }

    fun cancelCallConnectedPrompt() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_CALL_CONNECT_PROMPT)
    }
}

