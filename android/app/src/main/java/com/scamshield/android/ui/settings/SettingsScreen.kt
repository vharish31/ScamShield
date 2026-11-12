package com.scamshield.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamshield.android.ui.components.PulsingStatusDot
import com.scamshield.android.ui.components.SectionHeader
import com.scamshield.android.ui.theme.RiskHighColor
import com.scamshield.android.ui.theme.RiskLowColor
import com.scamshield.android.util.RoleAndPermissionHelper

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onSignedOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshRoleStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshRoleStatus()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Settings & Privacy",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure threat detection, connectivity & on-device retention",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        state.actionFeedback?.let { msg ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Account Profile Card
        item {
            SettingsCard(
                title = "Account Profile",
                icon = Icons.Filled.AccountCircle
            ) {
                Text(
                    text = state.user?.email ?: "Not signed in",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = state.draftDisplayName,
                    onValueChange = viewModel::updateDraftDisplayName,
                    label = { Text("Display Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::saveDisplayName,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Name", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.signOut(onSignedOut) },
                        colors = ButtonDefaults.buttonColors(containerColor = RiskHighColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Protection & Privacy Toggles
        item {
            SettingsCard(
                title = "Protection & Privacy Controls",
                icon = Icons.Filled.Security
            ) {
                SettingsToggleRow(
                    title = "Live Incoming Call Screening",
                    subtitle = "Identifies unknown callers locally while skipping saved contacts.",
                    checked = state.settings.liveScreeningEnabled,
                    onCheckedChange = viewModel::setLiveScreeningEnabled
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsToggleRow(
                    title = "Auto-Analyze Connected Unknown Calls",
                    subtitle = "Starts live monitoring on answer and stops + saves automatically on hang-up.",
                    checked = state.settings.autoAnalyzeOnCallConnect,
                    onCheckedChange = viewModel::setAutoAnalyzeOnCallConnect
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsToggleRow(
                    title = "Hide Details on Lock Screen",
                    subtitle = "Prevents transcripts or phone numbers from showing on locked devices.",
                    checked = state.settings.lockScreenPrivacyEnabled,
                    onCheckedChange = viewModel::setLockScreenPrivacyEnabled
                )
                Spacer(modifier = Modifier.padding(top = 4.dp))
                OutlinedButton(
                    onClick = {
                        RoleAndPermissionHelper.createCallScreeningRoleIntent(context)?.let {
                            settingsLauncher.launch(it)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (state.callScreeningRoleHeld) "Call Screening Role: Active (Manage)"
                        else "Set Scam Shield as Call Screener",
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = { settingsLauncher.launch(RoleAndPermissionHelper.openAppDetailsIntent(context)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Android App Permissions & Notifications")
                }
            }
        }

        // Backend Server Configuration
        item {
            SettingsCard(
                title = "FastAPI Backend Connection",
                icon = Icons.Filled.Cloud
            ) {
                OutlinedTextField(
                    value = state.draftBackendUrl,
                    onValueChange = viewModel::updateDraftUrl,
                    label = { Text("Backend Base URL") },
                    placeholder = { Text("http://127.0.0.1:8000") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.updateDraftUrl("http://127.0.0.1:8000") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("USB Local")
                    }
                    OutlinedButton(
                        onClick = { viewModel.updateDraftUrl("http://192.168.1.79:8000") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Wi-Fi PC")
                    }
                    OutlinedButton(
                        onClick = { viewModel.updateDraftUrl("http://10.0.2.2:8000") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Emulator")
                    }
                }
                Button(
                    onClick = viewModel::testBackendConnection,
                    enabled = !state.isTestingConnection,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (state.isTestingConnection) "Testing Connection..." else "Save & Test Backend Connection",
                        fontWeight = FontWeight.Bold
                    )
                }
                state.healthCheckMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Appearance & Data Retention
        item {
            SettingsCard(
                title = "Theme & Data Retention",
                icon = Icons.Filled.ColorLens
            ) {
                Text("App Theme", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.settings.darkModeOverride == null,
                        onClick = { viewModel.setDarkModeOverride(null) },
                        label = { Text("System") },
                        shape = RoundedCornerShape(10.dp)
                    )
                    FilterChip(
                        selected = state.settings.darkModeOverride == false,
                        onClick = { viewModel.setDarkModeOverride(false) },
                        label = { Text("Light") },
                        shape = RoundedCornerShape(10.dp)
                    )
                    FilterChip(
                        selected = state.settings.darkModeOverride == true,
                        onClick = { viewModel.setDarkModeOverride(true) },
                        label = { Text("Dark") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.padding(top = 4.dp))
                Text("Local History Retention", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { days ->
                        FilterChip(
                            selected = state.settings.retentionDays == days,
                            onClick = { viewModel.setRetentionDays(days) },
                            label = { Text("$days days") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.padding(top = 4.dp))
                OutlinedButton(
                    onClick = viewModel::clearLocalData,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = RiskHighColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Local Screening & Alert Cache", color = RiskHighColor, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Transparency & Limitations Card
        item {
            SettingsCard(
                title = "Privacy & Android Platform Disclosure",
                icon = Icons.Filled.Lock
            ) {
                Text(
                    text = "• Saved mobile contacts are matched locally on-device and NEVER uploaded.\n" +
                        "• Android prevents standard apps from accessing private cellular call downlink audio streams automatically.\n" +
                        "• Live Audio Analysis captures only user-initiated microphone input and can be stopped immediately.\n" +
                        "• AI scam risk scores are heuristic indicators, not definitive proof of fraud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
