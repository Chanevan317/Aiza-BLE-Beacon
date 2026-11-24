package com.aiza.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aiza.app.R

private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Manrope variable font (SIL Open Font License). */
val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private fun style(size: Int, weight: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val AizaTypography = Typography(
    displayLarge = style(56, 800, 60, -0.03),
    displayMedium = style(44, 800, 50, -0.03),
    displaySmall = style(36, 800, 42, -0.02),
    headlineLarge = style(32, 800, 38, -0.02),
    headlineMedium = style(28, 800, 34, -0.02),
    headlineSmall = style(24, 700, 30, -0.01),
    titleLarge = style(22, 700, 28, -0.01),
    titleMedium = style(17, 700, 24),
    titleSmall = style(15, 700, 20),
    bodyLarge = style(16, 500, 24),
    bodyMedium = style(14, 500, 20),
    bodySmall = style(12, 500, 16),
    labelLarge = style(15, 700, 20),
    labelMedium = style(13, 600, 16),
    labelSmall = style(11, 700, 14, 0.04),
)
