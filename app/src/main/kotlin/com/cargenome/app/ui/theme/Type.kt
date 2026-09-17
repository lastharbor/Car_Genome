package com.cargenome.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaults = Typography()

internal val CarGenomeTypography = defaults.copy(
    headlineSmall = defaults.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.Medium),
    labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.Medium),
)

/** Tabular-ish style for odometer, volume and money values in lists. */
internal val NumericStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
)
