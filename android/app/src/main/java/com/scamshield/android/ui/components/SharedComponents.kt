package com.scamshield.android.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.scamshield.android.domain.model.RiskLevel
import com.scamshield.android.domain.model.ScamCategory
import com.scamshield.android.ui.theme.RiskCriticalColor
import com.scamshield.android.ui.theme.RiskCriticalContainerDark
import com.scamshield.android.ui.theme.RiskCriticalContainerLight
import com.scamshield.android.ui.theme.RiskCriticalTextDark
import com.scamshield.android.ui.theme.RiskCriticalTextLight
import com.scamshield.android.ui.theme.RiskHighColor
import com.scamshield.android.ui.theme.RiskHighContainerDark
import com.scamshield.android.ui.theme.RiskHighContainerLight
import com.scamshield.android.ui.theme.RiskHighTextDark
import com.scamshield.android.ui.theme.RiskHighTextLight
import com.scamshield.android.ui.theme.RiskLowColor
import com.scamshield.android.ui.theme.RiskLowContainerDark
import com.scamshield.android.ui.theme.RiskLowContainerLight
import com.scamshield.android.ui.theme.RiskLowTextDark
import com.scamshield.android.ui.theme.RiskLowTextLight
import com.scamshield.android.ui.theme.RiskMediumColor
import com.scamshield.android.ui.theme.RiskMediumContainerDark
import com.scamshield.android.ui.theme.RiskMediumContainerLight
import com.scamshield.android.ui.theme.RiskMediumTextDark
import com.scamshield.android.ui.theme.RiskMediumTextLight
import java.io.File

data class RiskStyle(
    val accent: Color,
    val container: Color,
    val content: Color,
    val icon: ImageVector
)

@Composable
fun rememberRiskStyle(riskLevel: RiskLevel): RiskStyle {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (riskLevel) {
        RiskLevel.LOW -> RiskStyle(
            accent = RiskLowColor,
            container = if (isDark) RiskLowContainerDark else RiskLowContainerLight,
            content = if (isDark) RiskLowTextDark else RiskLowTextLight,
            icon = Icons.Filled.CheckCircle
        )
        RiskLevel.MEDIUM -> RiskStyle(
            accent = RiskMediumColor,
            container = if (isDark) RiskMediumContainerDark else RiskMediumContainerLight,
            content = if (isDark) RiskMediumTextDark else RiskMediumTextLight,
            icon = Icons.Filled.Warning
        )
        RiskLevel.HIGH -> RiskStyle(
            accent = RiskHighColor,
            container = if (isDark) RiskHighContainerDark else RiskHighContainerLight,
            content = if (isDark) RiskHighTextDark else RiskHighTextLight,
            icon = Icons.Filled.Error
        )
        RiskLevel.CRITICAL -> RiskStyle(
            accent = RiskCriticalColor,
            container = if (isDark) RiskCriticalContainerDark else RiskCriticalContainerLight,
            content = if (isDark) RiskCriticalTextDark else RiskCriticalTextLight,
            icon = Icons.Filled.Shield
        )
    }
}

/**
 * Pulsing status dot indicating real-time activity (e.g. mic recording, call active)
 */
@Composable
fun PulsingStatusDot(
    color: Color = RiskHighColor,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier.size(size * 1.6f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha * 0.45f))
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * Consistent section header with hierarchy, optional subtitle and trailing action
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

/**
 * Animated Risk Level Badge with smooth color transitions
 */
@Composable
fun RiskLevelBadge(
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier
) {
    val style = rememberRiskStyle(riskLevel)
    val animatedAccent by animateColorAsState(targetValue = style.accent, label = "riskAccent")
    val animatedContainer by animateColorAsState(targetValue = style.container, label = "riskContainer")
    val animatedContent by animateColorAsState(targetValue = style.content, label = "riskContent")

    Surface(
        modifier = modifier,
        color = animatedContainer,
        contentColor = animatedContent,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, animatedAccent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = riskLevel.label,
                tint = animatedAccent,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = riskLevel.apiValue,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Premium Risk Score Card featuring animated circular arc meter & severity feedback
 */
@Composable
fun RiskScoreCard(
    score: Int,
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier
) {
    val style = rememberRiskStyle(riskLevel)
    val clampedScore = score.coerceIn(0, 100)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedScore / 100f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "scoreArcProgress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = style.container),
        border = BorderStroke(1.5.dp, style.accent.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(style.accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = null,
                            tint = style.accent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = riskLevel.label,
                            style = MaterialTheme.typography.titleLarge,
                            color = style.content,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Risk Score: $score / 100",
                            style = MaterialTheme.typography.bodyMedium,
                            color = style.content.copy(alpha = 0.88f)
                        )
                    }
                }

                // Circular Meter with Big Percentage Text
                Box(
                    modifier = Modifier.size(68.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val trackColor = style.accent.copy(alpha = 0.18f)
                    val arcColor = style.accent
                    Canvas(modifier = Modifier.size(64.dp)) {
                        val strokeWidth = 6.dp.toPx()
                        val arcSize = size.width - strokeWidth
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        // Background track
                        drawArc(
                            color = trackColor,
                            startAngle = 140f,
                            sweepAngle = 260f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        // Active animated arc
                        drawArc(
                            color = arcColor,
                            startAngle = 140f,
                            sweepAngle = 260f * animatedProgress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "$score%",
                        style = MaterialTheme.typography.titleMedium,
                        color = style.accent,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Visual Progress Indicator
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = style.accent,
                trackColor = style.accent.copy(alpha = 0.22f),
                strokeCap = StrokeCap.Round
            )

            // Severity Message
            Text(
                text = riskLevel.warningMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = style.content,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Heuristic Disclaimer Banner with clean typography and optional expand
 */
@Composable
fun HeuristicDisclaimerBanner(
    disclaimer: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Disclaimer",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "AI Advisory Disclaimer",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = disclaimer.ifBlank {
                        "An AI risk score is an indicator based on linguistic patterns, not proof that a caller is fraudulent."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }
    }
}

/**
 * Flow layout for flagged scam pattern badges with category badges
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatternBadgeFlow(
    patterns: List<String>,
    modifier: Modifier = Modifier
) {
    if (patterns.isEmpty()) {
        Text(
            text = "No suspicious manipulation patterns flagged.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        patterns.forEach { key ->
            val category = ScamCategory.fromApiKey(key)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text(
                        text = category?.displayName ?: ScamCategory.displayLabelFor(key),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    if (category != null) {
                        Text(
                            text = category.shortExplanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium CTA button with elevation, loading spinner and icon support
 */
@Composable
fun ScamPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor.copy(alpha = 0.4f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp,
            disabledElevation = 0.dp
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * High-craft Empty State representation with subtle icon circle and call to action
 */
@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(6.dp))
                ScamPrimaryButton(
                    text = actionLabel,
                    onClick = onAction
                )
            }
        }
    }
}

/**
 * Elegant audio clip playback row with waveform visualizer and playback controls
 */
@Composable
fun AudioClipPlayerRow(
    clipPath: String,
    title: String,
    subtitle: String,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fileExists = remember(clipPath) { File(clipPath).exists() }
    var isPlaying by remember(clipPath) { mutableStateOf(false) }

    val exoPlayer = remember(clipPath, fileExists) {
        if (!fileExists) {
            null
        } else {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(clipPath))))
                prepare()
            }
        }
    }

    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                enabled = exoPlayer != null,
                onClick = {
                    exoPlayer?.let { player ->
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            if (player.playbackState == Player.STATE_ENDED) {
                                player.seekTo(0)
                            }
                            player.play()
                        }
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (exoPlayer != null) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause recording" else "Play recording",
                        tint = if (exoPlayer != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (fileExists) subtitle else "Audio file missing from device storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete recording",
                        tint = RiskHighColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
