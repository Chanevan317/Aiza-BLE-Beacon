package com.aiza.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand: deep indigo with an electric-violet accent; heat scale for hot/cold guidance.
object Brand {
    val Indigo = Color(0xFF4338CA)
    val IndigoBright = Color(0xFF6366F1)
    val Violet = Color(0xFF8B5CF6)
    val Ink = Color(0xFF0B0C14)
    val Paper = Color(0xFFF6F6FB)

    val Cold = Color(0xFF38BDF8)
    val Cool = Color(0xFF22D3EE)
    val Warm = Color(0xFFF59E0B)
    val Hot = Color(0xFFF43F5E)
}

internal val LightColors = lightColorScheme(
    primary = Brand.Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E1FF),
    onPrimaryContainer = Color(0xFF1B1464),
    secondary = Color(0xFF6D28D9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE4FF),
    onSecondaryContainer = Color(0xFF2B0A66),
    tertiary = Color(0xFF0E7490),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFF4FC),
    onTertiaryContainer = Color(0xFF07343F),
    background = Brand.Paper,
    onBackground = Color(0xFF14151F),
    surface = Brand.Paper,
    onSurface = Color(0xFF14151F),
    surfaceVariant = Color(0xFFE6E6F0),
    onSurfaceVariant = Color(0xFF5B5D6E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F0F7),
    surfaceContainerHigh = Color(0xFFEAEAF3),
    surfaceContainerHighest = Color(0xFFE3E3EE),
    outline = Color(0xFFB9BACB),
    outlineVariant = Color(0xFFDCDCE8),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFA5A8FF),
    onPrimary = Color(0xFF1B1464),
    primaryContainer = Color(0xFF2E2A8A),
    onPrimaryContainer = Color(0xFFE2E1FF),
    secondary = Color(0xFFC4B5FD),
    onSecondary = Color(0xFF2B0A66),
    secondaryContainer = Color(0xFF3F1D8A),
    onSecondaryContainer = Color(0xFFEDE4FF),
    tertiary = Color(0xFF67E8F9),
    onTertiary = Color(0xFF07343F),
    tertiaryContainer = Color(0xFF0B4F61),
    onTertiaryContainer = Color(0xFFCFF4FC),
    background = Brand.Ink,
    onBackground = Color(0xFFE8E8F2),
    surface = Brand.Ink,
    onSurface = Color(0xFFE8E8F2),
    surfaceVariant = Color(0xFF262838),
    onSurfaceVariant = Color(0xFFA3A5B8),
    surfaceContainerLowest = Color(0xFF07080E),
    surfaceContainerLow = Color(0xFF13141F),
    surfaceContainer = Color(0xFF171926),
    surfaceContainerHigh = Color(0xFF1E202E),
    surfaceContainerHighest = Color(0xFF262838),
    outline = Color(0xFF4A4C60),
    outlineVariant = Color(0xFF2C2E40),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5B1414),
    onErrorContainer = Color(0xFFFEE2E2),
)

/** Colours outside the Material scheme. */
@Immutable
data class AizaExtraColors(
    val success: Color,
    val warning: Color,
    val cardBorder: Color,
    val brandGradient: List<Color>,
    val heatScale: List<Color>,
)

internal val LightExtra = AizaExtraColors(
    success = Color(0xFF059669),
    warning = Color(0xFFD97706),
    cardBorder = Color(0x14000000),
    brandGradient = listOf(Brand.Indigo, Brand.Violet),
    heatScale = listOf(Brand.Cold, Brand.Cool, Brand.Warm, Brand.Hot),
)

internal val DarkExtra = AizaExtraColors(
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    cardBorder = Color(0x1AFFFFFF),
    brandGradient = listOf(Brand.IndigoBright, Brand.Violet),
    heatScale = listOf(Brand.Cold, Brand.Cool, Brand.Warm, Brand.Hot),
)

val LocalAizaColors = staticCompositionLocalOf { LightExtra }
