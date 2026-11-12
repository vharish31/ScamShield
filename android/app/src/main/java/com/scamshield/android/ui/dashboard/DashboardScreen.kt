package com.scamshield.android.ui.dashboard

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamshield.android.ui.components.EmptyStateCard
import com.scamshield.android.ui.components.PulsingStatusDot
import com.scamshield.android.ui.components.RiskLevelBadge
import com.scamshield.android.ui.components.ScamPrimaryButton
import com.scamshield.android.ui.components.SectionHeader
import com.scamshield.android.ui.theme.ActiveRecordingGradient
import com.scamshield.android.ui.theme.HeroShieldGradient
import com.scamshield.android.ui.theme.IndigoPrimary
import com.scamshield.android.ui.theme.IndigoPrimaryDark
import com.scamshield.android.ui.theme.RiskHighColor
import com.scamshield.android.ui.theme.RiskLowColor
import com.scamshield.android.ui.theme.RiskMediumColor
import com.scamshield.android.ui.theme.SafeGuardGradient
import com.scamshield.android.util.RoleAndPermissionHelper

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenLiveProtection: () -> Unit,
    onOpenAnalysisDetail: (String) -> Unit,
    onOpenContacts: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshStatus()
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshStatus()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Greeting Header
        item {
            val welcomeName = state.user?.displayName?.takeIf { it.isNotBlank() }
                ?: state.user?.email?.substringBefore("@")
                ?: "User"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = welcomeName.firstOrNull()?.uppercase() ?: "U",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column {
                        Text(
                            text = "Hello, $welcomeName",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI Threat Shield & Local Screening",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = viewModel::refreshStatus) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh status",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Protection Status Hero Card
        item {
            val isFullyProtected = state.callScreeningRoleHeld && state.contactsPermissionGranted
            val isLiveActive = state.isLiveAudioActive

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLiveActive || isFullyProtected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                border = if (isLiveActive) {
                    BorderStroke(2.dp, RiskHighColor)
                } else if (!isFullyProtected) {
                    BorderStroke(1.5.dp, RiskMediumColor)
                } else null,
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isLiveActive) {
                                Modifier.background(ActiveRecordingGradient)
                            } else if (isFullyProtected) {
                                Modifier.background(HeroShieldGradient)
                            } else {
                                Modifier
                            }
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFullyProtected || isLiveActive) {
                                            Color.White.copy(alpha = 0.2f)
                                        } else {
                                            RiskMediumColor.copy(alpha = 0.15f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        isLiveActive -> Icons.Filled.GraphicEq
                                        isFullyProtected -> Icons.Filled.Shield
                                        else -> Icons.Filled.Warning
                                    },
                                    contentDescription = null,
                                    tint = if (isFullyProtected || isLiveActive) Color.White else RiskMediumColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = when {
                                            isLiveActive -> "Live Monitoring Active"
                                            isFullyProtected -> "Call Screening Active"
                                            else -> "Action Needed for Protection"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFullyProtected || isLiveActive) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isLiveActive) {
                                        PulsingStatusDot(color = Color.White, size = 8.dp)
                                    }
                                }
                                Text(
                                    text = if (isFullyProtected) {
                                        "Saved contacts bypass screening automatically on your device."
                                    } else {
                                        "Enable Call Screening Role and Contacts access for automatic unknown caller screening."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isFullyProtected || isLiveActive) {
                                        Color.White.copy(alpha = 0.88f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        if (!state.callScreeningRoleHeld) {
                            Button(
                                onClick = {
                                    RoleAndPermissionHelper.createCallScreeningRoleIntent(context)?.let {
                                        roleLauncher.launch(it)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enable Call Screening Role", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!state.contactsPermissionGranted) {
                            OutlinedButton(
                                onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Allow Contacts Access (Required for Trusted Exclusion)", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // Contacts Exclusion Warning
        if (!state.contactsPermissionGranted) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Contacts permission is denied: trusted saved-contact exclusion cannot be guaranteed during incoming calls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Backend Connectivity Status Pill
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (state.backendOnline == true) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = if (state.backendOnline == true) RiskLowColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "AI Backend: ${state.backendStatusText}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.backendOnline == true) {
                        PulsingStatusDot(color = RiskLowColor, size = 6.dp)
                    }
                }
            }
        }

        // Metrics Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Trusted Contacts",
                        value = state.trustedContactsCount.toString(),
                        subtitle = "Protected on device",
                        accent = RiskLowColor,
                        icon = Icons.Filled.Contacts,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenContacts)
                    )
                    MetricCard(
                        title = "Unknown Screened",
                        value = state.unknownCallsScreenedCount.toString(),
                        subtitle = "Incoming unsaved calls",
                        accent = MaterialTheme.colorScheme.primary,
                        icon = Icons.Filled.Call,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "AI Analyses",
                        value = state.analysesCompletedCount.toString(),
                        subtitle = "Speech & text checks",
                        accent = MaterialTheme.colorScheme.primary,
                        icon = Icons.Filled.GraphicEq,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Alerts Flagged",
                        value = state.flaggedThreatsCount.toString(),
                        subtitle = "Medium to Critical",
                        accent = if (state.flaggedThreatsCount > 0) RiskHighColor else RiskLowColor,
                        icon = Icons.Filled.Shield,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Primary Action Button
        item {
            ScamPrimaryButton(
                text = if (state.isLiveAudioActive) {
                    "Open Active Live Audio Monitor"
                } else {
                    "Start Live Protection & Analysis"
                },
                icon = if (state.isLiveAudioActive) Icons.Filled.GraphicEq else Icons.Filled.Mic,
                onClick = onOpenLiveProtection,
                containerColor = if (state.isLiveAudioActive) RiskHighColor else MaterialTheme.colorScheme.primary
            )
        }

        // Recent Analyses Section
        item {
            SectionHeader(
                title = "Recent AI Analyses",
                subtitle = "Latest voice & text scam checks"
            )
        }

        if (state.recentAnalyses.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.History,
                    title = "No Call Analyses Yet",
                    message = "Start user-authorized live audio monitoring or paste suspicious call dialogue in Live Protection to run FastAPI scam detection.",
                    actionLabel = "Go to Live Protection",
                    onAction = onOpenLiveProtection
                )
            }
        } else {
            items(state.recentAnalyses, key = { it.analysisId }) { item ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAnalysisDetail(item.analysisId) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp).let {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RiskLevelBadge(riskLevel = item.riskLevel)
                                Text(
                                    text = "Score ${item.riskScore}/100",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = item.transcript,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Open report",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
