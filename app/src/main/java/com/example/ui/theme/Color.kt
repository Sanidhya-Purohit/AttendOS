package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================================
// BESPOKE CHROMATIC & EDITORIAL PALETTES
// Dark mode: Luxury Obsidian Midnight with Electric Orchid & Mint Fluorite
// Light mode: Editorial Alabaster with Crisp White Cards & Imperial Violet
// Fully reactive dynamic tokens ensuring entire app adapts to light & dark mode.
// ============================================================================

import androidx.compose.runtime.Composable

// --- Raw Dark Mode Tokens ---
val RawDarkBackground = Color(0xFF0C101C)       // Deep Obsidian Midnight (rich & refined)
val RawDarkSurface = Color(0xFF121829)          // Prismatic Dark Surface
val RawDarkSurfaceElevated = Color(0xFF192238)  // Elevated Dark Surface
val RawDarkSurfaceCard = Color(0xFF151D31)      // Crystalline Obsidian Card
val RawDarkBorder = Color(0xFF263450)           // Clean Architectural Stroke
val RawDarkBorderSubtle = Color(0xFF1B253B)

// --- Raw Light Mode Tokens ---
val RawLightBackground = Color(0xFFF1F5F9)      // Editorial Alabaster Canvas (rich cool slate canvas)
val RawLightSurface = Color(0xFFFFFFFF)         // Pure Luminous Porcelain
val RawLightSurfaceElevated = Color(0xFFE2E8F0) // Frosted Pearl Elevated
val RawLightSurfaceCard = Color(0xFFFFFFFF)     // Crisp White Card
val RawLightBorder = Color(0xFFCBD5E1)          // Defined Architectural Border
val RawLightBorderSubtle = Color(0xFFE2E8F0)

// --- Raw Dark Typography ---
val RawDarkTextPrimary = Color(0xFFF8FAFC)      // Pure Pearl White
val RawDarkTextSecondary = Color(0xFF94A3B8)    // Refined Cool Slate
val RawDarkTextMuted = Color(0xFF64748B)        // Muted Slate

// --- Raw Light Typography ---
val RawLightTextPrimary = Color(0xFF0F172A)     // Deep Obsidian Ink (Crystal clear high-contrast)
val RawLightTextSecondary = Color(0xFF334155)   // Refined Slate Charcoal
val RawLightTextMuted = Color(0xFF64748B)       // Soft Muted Slate

// --- Dynamic Reactive Accents (Electric Cyber in Dark, Rich Jewel Tones in Light) ---
val NeonCyan: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFFA855F7) else Color(0xFF7C3AED)
val NeonCyanDark: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFF9333EA) else Color(0xFF6D28D9)
val NeonEmerald: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFF00F5B4) else Color(0xFF059669)
val NeonMint: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFF10B981) else Color(0xFF059669)
val NeonGold: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
val NeonAmber: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFFFF9800) else Color(0xFFD97706)
val NeonOrange: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFFFF7043) else Color(0xFFEA580C)
val NeonCrimson: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFFF43F5E) else Color(0xFFDC2626)
val NeonRose: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFFFF4081) else Color(0xFFE11D48)
val NeonPurple: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFF8B5CF6) else Color(0xFF7C3AED)
val NeonBlue: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
val NeonAquamarine: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFF06B6D4) else Color(0xFF0891B2)

// High-contrast button text on saturated primary containers
val ButtonTextOnPrimary: Color = Color.White
val TextOnNeon: Color @Composable get() = Color.White

// --- Functional Tiers for Attendance % ---
val TierEmeraldColor = Color(0xFF00F5B4)     // >= 90% (Mint Fluorite)
val TierMintColor = Color(0xFF06B6D4)        // 85 - 89% (Aquamarine Prism)
val TierGoldColor = Color(0xFFF59E0B)        // 80 - 84% (Solar Topaz)
val TierWarningColor = Color(0xFFFF7043)     // 75 - 79% (Mandarin Coral)
val TierDangerColor = Color(0xFFF43F5E)      // < 75% Critical (Sunset Coral)
val TierSevereColor = Color(0xFFFF1744)      // < 65% Extreme

// --- AttendOS & AI Assistant Aesthetic Brushes ---
val AttendOSSparkleGold = Color(0xFFF59E0B)
val AttendOSGradBlue = Color(0xFF6366F1)
val AttendOSGradPurple = Color(0xFFA855F7)
val AttendOSGradPink = Color(0xFFF43F5E)
val AttendOSGradCyan = Color(0xFF06B6D4)

val GeminiSparkleGold = AttendOSSparkleGold
val GeminiGradBlue = AttendOSGradBlue
val GeminiGradPurple = AttendOSGradPurple
val GeminiGradPink = AttendOSGradPink
val GeminiGradCyan = AttendOSGradCyan

/**
 * Dynamic Semantic Accessors:
 * When used anywhere in Jetpack Compose UI, these dynamically resolve
 * to either Dark Mode or Light Mode values based on active theme!
 */
val DarkBackground: Color @Composable get() = AppTheme.colors.background
val DarkSurface: Color @Composable get() = AppTheme.colors.surface
val DarkSurfaceElevated: Color @Composable get() = AppTheme.colors.surfaceElevated
val DarkSurfaceCard: Color @Composable get() = AppTheme.colors.surfaceCard
val DarkBorder: Color @Composable get() = AppTheme.colors.border
val DarkBorderSubtle: Color @Composable get() = AppTheme.colors.borderSubtle

val LightBackground: Color = RawLightBackground
val LightSurface: Color = RawLightSurface
val LightSurfaceElevated: Color = RawLightSurfaceElevated
val LightSurfaceCard: Color = RawLightSurfaceCard
val LightBorder: Color = RawLightBorder
val LightBorderSubtle: Color = RawLightBorderSubtle

val TextPrimary: Color @Composable get() = AppTheme.colors.textPrimary
val TextSecondary: Color @Composable get() = AppTheme.colors.textSecondary
val TextMuted: Color @Composable get() = AppTheme.colors.textMuted
val TextAccent: Color @Composable get() = AppTheme.colors.primary

val AttendOSDarkBg: Color @Composable get() = AppTheme.colors.background
val AttendOSDarkSurface: Color @Composable get() = AppTheme.colors.surfaceCard
val AttendOSDarkInput: Color @Composable get() = AppTheme.colors.surfaceElevated
val AttendOSBorder: Color @Composable get() = AppTheme.colors.border
val AttendOSBubbleUser: Color @Composable get() = if (AppTheme.colors.isDark) Color(0xFF281F48) else Color(0xFFEDE9FE)
val AttendOSBlue: Color @Composable get() = AppTheme.colors.primary
val AttendOSPurple: Color @Composable get() = AppTheme.colors.accentPurple
val AttendOSCyan: Color @Composable get() = AppTheme.colors.secondary
val AttendOSChipBg: Color @Composable get() = AppTheme.colors.surfaceElevated
val AttendOSCardGlow: Color @Composable get() = AppTheme.colors.glassGlow

val GeminiDarkBg: Color @Composable get() = AttendOSDarkBg
val GeminiDarkSurface: Color @Composable get() = AttendOSDarkSurface
val GeminiDarkInput: Color @Composable get() = AttendOSDarkInput
val GeminiBorder: Color @Composable get() = AttendOSBorder
val GeminiBubbleUser: Color @Composable get() = AttendOSBubbleUser
val GeminiBlue: Color @Composable get() = AttendOSBlue
val GeminiPurple: Color @Composable get() = AttendOSPurple
val GeminiCyan: Color @Composable get() = AttendOSCyan
val GeminiChipBg: Color @Composable get() = AttendOSChipBg
val GeminiCardGlow: Color @Composable get() = AttendOSCardGlow

/**
 * Complete Theme Design System providing semantic tokens across both
 * Chromatic Dark mode and Vibrant Light mode.
 */
@Immutable
data class AttendOSPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceCard: Color,
    val border: Color,
    val borderSubtle: Color,
    val cardBorder: Color = border,
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val accentAmber: Color,
    val accentCoral: Color,
    val accentCyan: Color,
    val accentPurple: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val brandGradient: Brush,
    val cardGradient: Brush,
    val glassGlow: Color
)

val ChromaticDarkPalette = AttendOSPalette(
    isDark = true,
    background = RawDarkBackground,
    surface = RawDarkSurface,
    surfaceElevated = RawDarkSurfaceElevated,
    surfaceCard = RawDarkSurfaceCard,
    border = RawDarkBorder,
    borderSubtle = RawDarkBorderSubtle,
    cardBorder = RawDarkBorder,
    primary = Color(0xFFA855F7),        // Electric Orchid
    primaryVariant = Color(0xFF9333EA),
    secondary = Color(0xFF00F5B4),      // Mint Fluorite
    accentAmber = Color(0xFFF59E0B),    // Hyper Amber
    accentCoral = Color(0xFFF43F5E),    // Rose Punch
    accentCyan = Color(0xFF06B6D4),     // Cyan Prism
    accentPurple = Color(0xFF8B5CF6),
    textPrimary = RawDarkTextPrimary,
    textSecondary = RawDarkTextSecondary,
    textMuted = RawDarkTextMuted,
    brandGradient = Brush.horizontalGradient(
        listOf(Color(0xFFA855F7), Color(0xFF8B5CF6), Color(0xFF06B6D4))
    ),
    cardGradient = Brush.linearGradient(
        listOf(Color(0xFF151D31), Color(0xFF0F1524))
    ),
    glassGlow = Color(0x33A855F7)
)

val VibrantLightPalette = AttendOSPalette(
    isDark = false,
    background = RawLightBackground,
    surface = RawLightSurface,
    surfaceElevated = RawLightSurfaceElevated,
    surfaceCard = RawLightSurfaceCard,
    border = RawLightBorder,
    borderSubtle = RawLightBorderSubtle,
    cardBorder = RawLightBorder,
    primary = Color(0xFF7C3AED),        // Royal Imperial Violet
    primaryVariant = Color(0xFF6D28D9),
    secondary = Color(0xFF059669),      // Crisp Emerald
    accentAmber = Color(0xFFD97706),
    accentCoral = Color(0xFFE11D48),
    accentCyan = Color(0xFF0284C7),
    accentPurple = Color(0xFF7C3AED),
    textPrimary = RawLightTextPrimary,
    textSecondary = RawLightTextSecondary,
    textMuted = RawLightTextMuted,
    brandGradient = Brush.horizontalGradient(
        listOf(Color(0xFF7C3AED), Color(0xFF8B5CF6), Color(0xFF0284C7))
    ),
    cardGradient = Brush.linearGradient(
        listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC))
    ),
    glassGlow = Color(0x227C3AED)
)

val LocalAttendOSColors = staticCompositionLocalOf { ChromaticDarkPalette }

/**
 * Dynamic theme-reactive attendance tier colors.
 * Luminous electric cyan/mint/amber in dark mode;
 * deep jewel-tone emerald/sapphire/topaz/ruby in light mode for supreme legibility.
 */
val com.example.domain.AttendanceTier.displayColor: Color
    @Composable
    get() = if (AppTheme.colors.isDark) {
        this.color
    } else {
        when (this) {
            com.example.domain.AttendanceTier.ELITE -> Color(0xFF059669)       // Deep Emerald
            com.example.domain.AttendanceTier.EXCELLENT -> Color(0xFF0284C7)   // Deep Ocean Azure
            com.example.domain.AttendanceTier.HIGH_BUFFER -> Color(0xFF0891B2) // Rich Teal
            com.example.domain.AttendanceTier.SAFE_ZONE -> Color(0xFFD97706)   // Solar Amber
            com.example.domain.AttendanceTier.BORDERLINE -> Color(0xFFEA580C)  // Mandarin
            com.example.domain.AttendanceTier.SHORTAGE -> Color(0xFFDC2626)    // Ruby Coral
            com.example.domain.AttendanceTier.CRITICAL -> Color(0xFF991B1B)    // Deep Crimson
        }
    }

val com.example.domain.AttendanceTier.badgeBgColor: Color
    @Composable
    get() = if (AppTheme.colors.isDark) {
        this.color.copy(alpha = 0.15f)
    } else {
        this.displayColor.copy(alpha = 0.12f)
    }


