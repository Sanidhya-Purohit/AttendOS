package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA855F7),
    onPrimary = Color(0xFF0C101C),
    primaryContainer = Color(0xFF2E1065),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = Color(0xFF00F5B4),
    onSecondary = Color(0xFF0C101C),
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color(0xFF0C101C),
    background = RawDarkBackground,
    onBackground = RawDarkTextPrimary,
    surface = RawDarkSurface,
    onSurface = RawDarkTextPrimary,
    surfaceVariant = RawDarkSurfaceElevated,
    onSurfaceVariant = RawDarkTextSecondary,
    error = Color(0xFFF43F5E),
    onError = Color.White,
    outline = RawDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = RawLightBackground,
    onBackground = RawLightTextPrimary,
    surface = RawLightSurface,
    onSurface = RawLightTextPrimary,
    surfaceVariant = RawLightSurfaceElevated,
    onSurfaceVariant = RawLightTextSecondary,
    error = Color(0xFFE11D48),
    onError = Color.White,
    outline = RawLightBorder
)

object AppTheme {
    val colors: AttendOSPalette
        @Composable
        get() = LocalAttendOSColors.current
}

@Composable
fun MyApplicationTheme(
    themeMode: String = "DARK", // "DARK", "LIGHT", "SYSTEM"
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "LIGHT" -> false
        "SYSTEM" -> systemInDark
        else -> true // default to Chromatic Dark
    }

    val activePalette = if (isDark) ChromaticDarkPalette else VibrantLightPalette
    val activeColorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val barColor = activePalette.background.toArgb()
                window.statusBarColor = barColor
                window.navigationBarColor = barColor
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalAttendOSColors provides activePalette) {
        MaterialTheme(
            colorScheme = activeColorScheme,
            typography = Typography,
            content = content
        )
    }
}

