package com.opus.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Opus: deep midnight violet + warm brass accent.
val Brass = Color(0xFFE8B04B)
val BrassDim = Color(0xFFB9832F)
val Violet = Color(0xFF9D7BFF)
val Midnight = Color(0xFF0C0A12)
val MidnightSurface = Color(0xFF14101D)
val MidnightCard = Color(0xFF1C1628)
val MidnightElevated = Color(0xFF241C33)
val TextPrimary = Color(0xFFF5F0E6)
val TextSecondary = Color(0xFFA79FB5)

private val OpusDarkColors = darkColorScheme(
    primary = Brass,
    onPrimary = Color(0xFF1A1206),
    primaryContainer = Color(0xFF3A2A10),
    onPrimaryContainer = Brass,
    secondary = Violet,
    onSecondary = Color.White,
    background = Midnight,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = MidnightCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = MidnightElevated,
    outline = Color(0xFF3A3247),
    error = Color(0xFFFF8A80)
)

private val OpusTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp, color = TextSecondary),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp)
)

@Composable
fun OpusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpusDarkColors,
        typography = OpusTypography,
        content = content
    )
}

/** Keep dark always: Opus is a dark-mode-first experience. */
@Composable
fun isOpusDark(): Boolean = isSystemInDarkTheme().let { true }
