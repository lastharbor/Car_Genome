package com.cargenome.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Colors for maintenance/reminder states. They live outside the Material scheme
 * because dynamic color would otherwise recolor "overdue" into something calm.
 */
@Immutable
data class StatusColors(
    val ok: Color,
    val soon: Color,
    val overdue: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val LightStatusColors = StatusColors(
    ok = StatusOk,
    soon = StatusSoon,
    overdue = StatusOverdue,
    warningContainer = WarningContainer,
    onWarningContainer = OnWarningContainer,
)

private val DarkStatusColors = StatusColors(
    ok = StatusOkDark,
    soon = StatusSoonDark,
    overdue = StatusOverdueDark,
    warningContainer = WarningContainerDark,
    onWarningContainer = OnWarningContainerDark,
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

private val LightScheme = lightColorScheme(
    primary = BrandBlue,
    surface = BrandSurfaceLight,
    background = BrandSurfaceLight,
)

private val DarkScheme = darkColorScheme(
    primary = BrandBlueDark,
    surface = BrandInk,
    background = BrandInk,
)

@Composable
fun CarGenomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val colorScheme = if (darkTheme && amoled) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF141414),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF222222),
        )
    } else {
        baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CarGenomeTypography,
            content = content,
        )
    }
}
