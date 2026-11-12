package com.scamshield.android.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamshield.android.domain.model.RecordingSyncStatus
import com.scamshield.android.domain.model.RiskLevel
import com.scamshield.android.domain.model.ScamCategory
import com.scamshield.android.ui.components.AudioClipPlayerRow
import com.scamshield.android.ui.components.EmptyStateCard
import com.scamshield.android.ui.components.RiskLevelBadge
import com.scamshield.android.ui.theme.RiskHighColor
import com.scamshield.android.ui.theme.RiskLowColor
import com.scamshield.android.ui.theme.RiskMediumColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onOpenAnalysisDetail: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Protection History",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Audit trail of screened calls & recorded sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = viewModel::refreshHistory) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        TabRow(
            selectedTabIndex = state.selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = state.selectedTabIndex == 0,
                onClick = { viewModel.selectTab(0) },
                text = {
                    Text(
                        text = "Analyses (${state.remoteHistory.size})",
                        fontWeight = if (state.selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
            Tab(
                selected = state.selectedTabIndex == 1,
                onClick = { viewModel.selectTab(1) },
                text = {
                    Text(
                        text = "Recordings (${state.recordings.size})",
                        fontWeight = if (state.selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
            Tab(
                selected = state.selectedTabIndex == 2,
                onClick = { viewModel.selectTab(2) },
                text = {
                    Text(
                        text = "Screened (${state.screeningEvents.size})",
                        fontWeight = if (state.selectedTabIndex == 2) FontWeight.Bold else FontWeight.Medium
                    )
                }
            )
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            placeholder = { Text("Search transcripts, numbers or notes...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        if (state.selectedTabIndex == 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.selectedRiskFilter == null,
                    onClick = { viewModel.selectRiskFilter(null) },
                    label = { Text("All") },
                    shape = RoundedCornerShape(10.dp)
                )
                RiskLevel.entries.forEach { level ->
                    FilterChip(
                        selected = state.selectedRiskFilter == level,
                        onClick = { viewModel.selectRiskFilter(level) },
                        label = { Text(level.apiValue) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.remoteHistory.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.History,
                            title = "No Matching Analyses",
                            message = state.errorMessage
                                ?: "Analyses run via Live Audio or Transcript Verification appear here."
                        )
                    }
                } else {
                    items(state.remoteHistory, key = { it.analysisId }) { item ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenAnalysisDetail(item.analysisId) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    RiskLevelBadge(riskLevel = item.riskLevel)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Score ${item.riskScore}/100",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(onClick = { viewModel.deleteAnalysis(item.analysisId) }) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete",
                                                tint = RiskHighColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = item.transcriptPreview.ifBlank { "(No preview available)" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.detectedPatterns.isNotEmpty()) {
                                    Text(
                                        text = item.detectedPatterns.joinToString(" • ") {
                                            ScamCategory.displayLabelFor(it)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (state.selectedTabIndex == 1) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.recordings.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Mic,
                            title = "No Recorded Calls Saved Yet",
                            message = "When you run Live Audio Analysis and tap Stop, the recorded call audio is saved locally and uploaded to the backend database."
                        )
                    }
                } else {
                    items(state.recordings, key = { it.recordingId }) { rec ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rec.numberMasked ?: "Live Audio Session",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val (syncLabel, syncColor) = when (rec.syncStatus) {
                                        RecordingSyncStatus.SYNCED_ANALYSIS -> "Saved in DB" to RiskLowColor
                                        RecordingSyncStatus.UPLOADED_AUDIO_ONLY -> "Audio in DB" to RiskLowColor
                                        RecordingSyncStatus.LOCAL_PENDING -> "Pending Sync" to RiskMediumColor
                                        RecordingSyncStatus.FAILED -> "Sync Error" to RiskHighColor
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = syncColor.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = syncLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = syncColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = rec.transcriptSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                AudioClipPlayerRow(
                                    clipPath = rec.localFilePath,
                                    title = "${rec.durationSeconds}s • ${(rec.fileSizeBytes / 1024L).coerceAtLeast(1)} KB",
                                    subtitle = formatTimestamp(rec.timestampMillis),
                                    onDelete = { viewModel.deleteRecording(rec.recordingId) }
                                )

                                if (rec.analysisId != null) {
                                    OutlinedButton(
                                        onClick = { onOpenAnalysisDetail(rec.analysisId) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Open Linked Analysis Report", fontWeight = FontWeight.Bold)
                                    }
                                } else if (rec.syncStatus == RecordingSyncStatus.LOCAL_PENDING ||
                                    rec.syncStatus == RecordingSyncStatus.FAILED
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.syncRecording(rec.recordingId) },
                                        enabled = state.syncingRecordingId != rec.recordingId,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (state.syncingRecordingId == rec.recordingId) "Syncing..."
                                            else "Sync Recording to Database",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.screeningEvents.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Call,
                            title = "No Unknown Call Screenings Yet",
                            message = "Incoming calls from unknown numbers will appear here. Saved contacts are trusted and excluded completely."
                        )
                    }
                } else {
                    items(state.screeningEvents, key = { it.id }) { evt ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
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
                                        text = evt.displayLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatTimestamp(evt.timestampMillis),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = evt.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val fmt = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}
