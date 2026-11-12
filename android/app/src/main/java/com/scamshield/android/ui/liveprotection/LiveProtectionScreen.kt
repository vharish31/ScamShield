package com.scamshield.android.ui.liveprotection

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamshield.android.ui.components.AudioClipPlayerRow
import com.scamshield.android.ui.components.HeuristicDisclaimerBanner
import com.scamshield.android.ui.components.PatternBadgeFlow
import com.scamshield.android.ui.components.PulsingStatusDot
import com.scamshield.android.ui.components.RiskScoreCard
import com.scamshield.android.ui.components.ScamPrimaryButton
import com.scamshield.android.ui.components.SectionHeader
import com.scamshield.android.ui.theme.ActiveRecordingGradient
import com.scamshield.android.ui.theme.RiskHighColor
import com.scamshield.android.ui.theme.RiskLowColor
import com.scamshield.android.util.RoleAndPermissionHelper
import java.util.Locale

@Composable
fun LiveProtectionScreen(
    viewModel: LiveProtectionViewModel,
    onOpenAnalysisDetail: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        if (granted) {
            viewModel.openConsentDialog()
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissions()
    }

    val phoneStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    if (state.showConsentDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConsentDialog,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Start Live Audio Analysis?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Android does not permit third-party apps to read both sides of cellular call streams internally. " +
                        "When you tap Confirm, Scam Shield captures microphone audio (best with Speakerphone on), " +
                        "shows an active notification, and sends short WAV audio segments to your configured FastAPI backend for Faster-Whisper transcription and scam scoring. " +
                        "You can stop immediately at any time."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::startLiveAudioMonitoring,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm & Start Mic Analysis", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConsentDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode A: Incoming Call Screening Status
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.callScreeningRoleHeld) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = null,
                                tint = if (state.callScreeningRoleHeld) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Mode A: Incoming Call Screening",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (state.callScreeningRoleHeld) {
                                    PulsingStatusDot(color = RiskLowColor, size = 6.dp)
                                }
                            }
                            Text(
                                text = if (state.callScreeningRoleHeld) {
                                    "Active — identifies unknown callers locally; saved contacts are excluded."
                                } else {
                                    "Inactive — grant Call Screening role to detect unknown callers automatically."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!state.callScreeningRoleHeld) {
                        OutlinedButton(
                            onClick = {
                                RoleAndPermissionHelper.createCallScreeningRoleIntent(context)?.let {
                                    roleLauncher.launch(it)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable Call Screening Role", fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Auto-Analyze Unknown Calls on Connect",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Automatically starts live analysis on answer and stops on hang-up (trusted contacts skipped).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.autoAnalyzeOnCallConnect,
                            onCheckedChange = viewModel::setAutoAnalyzeOnCallConnect
                        )
                    }

                    if (state.autoAnalyzeOnCallConnect && !state.phoneStatePermissionGranted) {
                        OutlinedButton(
                            onClick = { phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Phone State Permission for Auto Start/Stop")
                        }
                    }

                    state.latestScreeningEvent?.let { evt ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Last Screened Unknown Call: ${evt.displayLabel}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${evt.notes} (Metadata check only — speech is not analyzed automatically.)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mode B: User-Authorized Live Audio Analysis
        item {
            val monitoring = state.monitoring
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(if (monitoring.isMonitoring) 4.dp else 2.dp),
                border = if (monitoring.isMonitoring) BorderStroke(2.dp, RiskHighColor) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (monitoring.isMonitoring) RiskHighColor.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = if (monitoring.isMonitoring) RiskHighColor else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Mode B: Live Audio Analysis",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (monitoring.isMonitoring) {
                                    PulsingStatusDot(color = RiskHighColor, size = 6.dp)
                                }
                            }
                            Text(
                                text = monitoring.audioSourceDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Speakerphone Guidance Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "For cellular calls, switch to Speakerphone so your mic hears the caller clearly. Android prevents direct internal recording of cellular downlink.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.callerPhoneInput,
                        onValueChange = viewModel::updateCallerPhoneInput,
                        label = { Text("Optional Unknown Caller Number (kept local)") },
                        placeholder = { Text("e.g., +91 98765 43210") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (monitoring.isMonitoring) {
                        val mins = monitoring.elapsedSeconds / 60
                        val secs = monitoring.elapsedSeconds % 60
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PulsingStatusDot(color = RiskHighColor, size = 8.dp)
                                    Text(
                                        text = "MIC MONITORING ACTIVE • ${String.format(Locale.US, "%02d:%02d", mins, secs)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { monitoring.audioAmplitude },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = RiskHighColor,
                                    trackColor = RiskHighColor.copy(alpha = 0.2f)
                                )
                                Text(
                                    text = monitoring.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::analyzeCurrentBufferNow,
                                enabled = !monitoring.isUploadingChunk,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (monitoring.isUploadingChunk) "Analyzing..." else "Analyze Now")
                            }
                            Button(
                                onClick = viewModel::stopLiveAudioMonitoring,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RiskHighColor),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (monitoring.isFinalizingRecording) {
                        ScamPrimaryButton(
                            text = "Saving Recording to Database...",
                            loading = true,
                            onClick = {}
                        )
                    } else {
                        ScamPrimaryButton(
                            text = "Start Live Analysis",
                            icon = Icons.Filled.Mic,
                            onClick = {
                                if (state.microphonePermissionGranted) {
                                    viewModel.openConsentDialog()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        )
                    }

                    monitoring.errorMessage?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = viewModel::dismissAudioError) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }

                    if (monitoring.transcriptSegments.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Captured Live Transcript (${monitoring.chunksUploaded} chunks)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = {
                                clipboardManager.setText(
                                    AnnotatedString(monitoring.transcriptSegments.joinToString("\n"))
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy transcript",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = monitoring.transcriptSegments.joinToString("\n"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Default,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    if (!monitoring.isMonitoring && !monitoring.lastRecordingPath.isNullOrBlank()) {
                        AudioClipPlayerRow(
                            clipPath = monitoring.lastRecordingPath,
                            title = "Recorded Call Audio",
                            subtitle = monitoring.statusMessage,
                            onDelete = viewModel::deleteLastClip
                        )
                    }
                }
            }
        }

        // Latest Live Analysis Summary
        state.monitoring.latestAnalysis?.let { result ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "Latest Live Threat Assessment",
                        subtitle = "Instant heuristic scam report"
                    )
                    RiskScoreCard(score = result.riskScore, riskLevel = result.riskLevel)
                    PatternBadgeFlow(patterns = result.detectedPatterns)
                    HeuristicDisclaimerBanner(disclaimer = result.confidenceDisclaimer)
                    OutlinedButton(
                        onClick = { onOpenAnalysisDetail(result.analysisId) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Full Analysis & Recommendations", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Manual Call Transcript Check (Fallback & Testing)
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.TextFields,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Analyze Call Transcript Manually",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Paste suspicious call dialogue to test AI detection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.manualTranscriptInput,
                        onValueChange = viewModel::updateManualTranscript,
                        label = { Text("Caller Statement / Transcript") },
                        placeholder = { Text("e.g., 'We have detected unusual activity on your bank card. Transfer funds to verify.'") },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    state.textAnalysisError?.let { err ->
                        Text(
                            text = err,
                            color = RiskHighColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    ScamPrimaryButton(
                        text = "Analyze Text with AI",
                        loading = state.isAnalyzingText,
                        onClick = {
                            viewModel.analyzeManualTranscript { result ->
                                onOpenAnalysisDetail(result.analysisId)
                            }
                        }
                    )
                }
            }
        }
    }
}
