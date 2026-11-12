package com.scamshield.android.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================================
// Brand & Core Security Palette - Modern Indigo & Cobalt Tonal Hierarchy
// ============================================================================
val IndigoPrimary = Color(0xFF4F46E5)
val IndigoPrimaryDark = Color(0xFF3730A3)
val IndigoPrimaryLight = Color(0xFF818CF8)
val IndigoContainerLight = Color(0xFFEEF2FF)
val IndigoContainerDark = Color(0xFF1E1B4B)

// Secondary & Accent Accents
val AccentCyan = Color(0xFF06B6D4)
val AccentTeal = Color(0xFF0D9488)
val ShieldGold = Color(0xFFF59E0B)

// ============================================================================
// Neutral Surfaces - Expressive Light Palette
// ============================================================================
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F5F9)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF8FAFC)
val LightSurfaceContainer = Color(0xFFF1F5F9)
val LightSurfaceContainerHigh = Color(0xFFE2E8F0)
val LightSurfaceContainerHighest = Color(0xFFCBD5E1)

val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextTertiary = Color(0xFF94A3B8)
val LightBorder = Color(0xFFE2E8F0)
val LightBorderSubtle = Color(0xFFF1F5F9)

// ============================================================================
// Neutral Surfaces - Deep Modern OLED/Slate Dark Palette
// ============================================================================
val DarkBackground = Color(0xFF090D16)
val DarkSurface = Color(0xFF111726)
val DarkSurfaceVariant = Color(0xFF1B2337)
val DarkSurfaceContainerLowest = Color(0xFF060910)
val DarkSurfaceContainerLow = Color(0xFF0D121F)
val DarkSurfaceContainer = Color(0xFF141C2E)
val DarkSurfaceContainerHigh = Color(0xFF1E283F)
val DarkSurfaceContainerHighest = Color(0xFF2B3752)

val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)
val DarkTextTertiary = Color(0xFF64748B)
val DarkBorder = Color(0xFF242E46)
val DarkBorderSubtle = Color(0xFF182033)

// ============================================================================
// Semantic Risk Tiers - High Contrast & Accessible
// ============================================================================
// Low / Safe Tier
val RiskLowColor = Color(0xFF10B981)
val RiskLowDark = Color(0xFF059669)
val RiskLowContainerLight = Color(0xFFECFDF5)
val RiskLowContainerDark = Color(0xFF064E3B)
val RiskLowTextLight = Color(0xFF065F46)
val RiskLowTextDark = Color(0xFF6EE7B7)

// Medium / Warning Tier
val RiskMediumColor = Color(0xFFF59E0B)
val RiskMediumDark = Color(0xFFD97706)
val RiskMediumContainerLight = Color(0xFFFFFBEB)
val RiskMediumContainerDark = Color(0xFF78350F)
val RiskMediumTextLight = Color(0xFF92400E)
val RiskMediumTextDark = Color(0xFFFDE68A)

// High / Danger Tier
val RiskHighColor = Color(0xFFEF4444)
val RiskHighDark = Color(0xFFDC2626)
val RiskHighContainerLight = Color(0xFFFEF2F2)
val RiskHighContainerDark = Color(0xFF7F1D1D)
val RiskHighTextLight = Color(0xFF991B1B)
val RiskHighTextDark = Color(0xFFFCA5A5)

// Critical / Severe Threat Tier
val RiskCriticalColor = Color(0xFFB91C1C)
val RiskCriticalDark = Color(0xFF7F1D1D)
val RiskCriticalContainerLight = Color(0xFFFEE2E2)
val RiskCriticalContainerDark = Color(0xFF450A0A)
val RiskCriticalTextLight = Color(0xFF7F1D1D)
val RiskCriticalTextDark = Color(0xFFF87171)

// ============================================================================
// Gradients & Visual Flourishes
// ============================================================================
val HeroShieldGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF4F46E5),
        Color(0xFF3730A3),
        Color(0xFF1E1B4B)
    )
)

val ActiveRecordingGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFDC2626),
        Color(0xFF991B1B)
    )
)

val SafeGuardGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF059669),
        Color(0xFF047857)
    )
)
