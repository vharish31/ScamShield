package com.scamshield.android.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamshield.android.ui.components.ScamPrimaryButton
import com.scamshield.android.ui.theme.HeroShieldGradient
import com.scamshield.android.ui.theme.RiskLowColor
import com.scamshield.android.util.RoleAndPermissionHelper

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onContinue: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissionStatus()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissionStatus()
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissionStatus()
    }

    val phoneStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissionStatus()
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissionStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Hero Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(HeroShieldGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column {
                Text(
                    text = "Welcome to Scam Shield",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Private caller screening & AI audio threat detection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        PermissionStepCard(
            stepNumber = "1",
            icon = Icons.Filled.Contacts,
            title = "Trusted Contacts (On-Device Only)",
            description = "Saved contacts are automatically trusted and excluded from scam monitoring. " +
                "Your contact list NEVER leaves your device. Without Contacts access, saved-contact exclusion cannot be guaranteed.",
            granted = state.contactsGranted,
            grantedLabel = if (state.syncedContactsCount > 0) {
                "Granted (${state.syncedContactsCount} numbers protected locally)"
            } else {
                "Granted"
            },
            buttonLabel = "Allow Contacts Access",
            onAction = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }
        )

        PermissionStepCard(
            stepNumber = "2",
            icon = Icons.Filled.Call,
            title = "Caller ID & Screening Role",
            description = "Allows Android to notify Scam Shield of incoming unknown numbers before alerting you. " +
                "Scam Shield never auto-blocks calls by default and cannot intercept private cellular call audio.",
            granted = state.callScreeningHeld,
            grantedLabel = "Active Call Screening App",
            buttonLabel = if (state.callScreeningSupported) "Set as Call Screener" else "Open Default Apps",
            onAction = {
                RoleAndPermissionHelper.createCallScreeningRoleIntent(context)?.let {
                    roleLauncher.launch(it)
                }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionStepCard(
                stepNumber = "3",
                icon = Icons.Filled.Notifications,
                title = "Security Alerts & Warnings",
                description = "Displays alerts for unknown callers and AI-detected high-risk patterns without exposing speech transcripts on your lock screen.",
                granted = state.notificationsGranted,
                grantedLabel = "Notifications Enabled",
                buttonLabel = "Allow Notifications",
                onAction = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            )
        }

        PermissionStepCard(
            stepNumber = "4",
            icon = Icons.Filled.Mic,
            title = "Microphone (Live Analysis)",
            description = "Required for live audio analysis when you tap Start or when an unknown call connects. " +
                "Switch calls to Speakerphone so the microphone hears the caller clearly.",
            granted = state.microphoneGranted,
            grantedLabel = "Microphone Permission Ready",
            buttonLabel = "Allow Microphone",
            onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        )

        PermissionStepCard(
            stepNumber = "5",
            icon = Icons.Filled.PhoneInTalk,
            title = "Call Connect / Hang-up Automation",
            description = "Allows Scam Shield to detect when an unknown call connects and automatically stop + save the recording when the call disconnects. Trusted contacts remain completely excluded.",
            granted = state.phoneStateGranted,
            grantedLabel = "Call Lifecycle Automation Ready",
            buttonLabel = "Allow Phone State",
            onAction = { phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        ScamPrimaryButton(
            text = "Continue to Scam Shield",
            onClick = { viewModel.completeOnboarding(onContinue) }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionStepCard(
    stepNumber: String,
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    grantedLabel: String,
    buttonLabel: String,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (granted) 1.dp else 2.dp),
        border = if (granted) BorderStroke(1.dp, RiskLowColor.copy(alpha = 0.5f)) else null,
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (granted) RiskLowColor.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (granted) Icons.Filled.CheckCircle else icon,
                        contentDescription = null,
                        tint = if (granted) RiskLowColor else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Step $stepNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (granted) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = RiskLowColor.copy(alpha = 0.12f),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = RiskLowColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = grantedLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = RiskLowColor
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(buttonLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
