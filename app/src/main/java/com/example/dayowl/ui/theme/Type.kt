package com.example.dayowl.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.dayowl.R

/**
 * One variable font file, four instances. Large text is tracked tight and set heavy; small text is
 * tracked open. That weight-and-tracking contrast is the whole typographic idea — nothing else.
 */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) = Font(
    resId = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val InterFamily = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold)
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double
) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp
)

val Typography = Typography(
    displayLarge = style(57, 64, FontWeight.Bold, -1.5),
    displayMedium = style(45, 52, FontWeight.Bold, -1.0),
    displaySmall = style(36, 44, FontWeight.Bold, -0.6),

    headlineLarge = style(32, 40, FontWeight.Bold, -0.5),
    headlineMedium = style(28, 36, FontWeight.Bold, -0.4),
    headlineSmall = style(24, 32, FontWeight.SemiBold, -0.3),

    titleLarge = style(22, 28, FontWeight.SemiBold, -0.2),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.0),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),

    bodyLarge = style(16, 26, FontWeight.Normal, 0.0),
    bodyMedium = style(14, 22, FontWeight.Normal, 0.1),
    bodySmall = style(12, 18, FontWeight.Normal, 0.2),

    labelLarge = style(14, 20, FontWeight.SemiBold, 0.3),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.4),
    labelSmall = style(11, 16, FontWeight.SemiBold, 0.6)
)
