package com.spendly.app.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────────────
//  Monochrome OLED Black & Warm White Color Scheme
// ─────────────────────────────────────────────────────────────────────

private val OledMonochromeColorScheme = darkColorScheme(
    primary = WarmWhite,
    onPrimary = OledBlack,
    primaryContainer = OledSurface3,
    onPrimaryContainer = WarmWhite,
    secondary = WarmWhiteMuted,
    onSecondary = OledBlack,
    secondaryContainer = OledSurface2,
    onSecondaryContainer = WarmWhite,
    tertiary = WarmWhite,
    onTertiary = OledBlack,
    background = OledBlack,
    onBackground = WarmWhite,
    surface = OledBlack,
    onSurface = WarmWhite,
    surfaceVariant = OledSurface1,
    onSurfaceVariant = WarmWhiteMuted,
    surfaceContainer = OledSurface2,
    surfaceContainerHigh = OledSurface3,
    surfaceContainerHighest = OledSurface4,
    outline = OledBorder,
    outlineVariant = OledBorderSubtle,
    error = WarmWhite,
    onError = OledBlack,
    errorContainer = OledSurface3,
    onErrorContainer = WarmWhite,
    inverseSurface = WarmWhite,
    inverseOnSurface = OledBlack,
    inversePrimary = OledBlack,
    scrim = OledBlack
)

@Composable
fun SpendlyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // Strictly OLED black and warm white everywhere
    val colorScheme = OledMonochromeColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                // White status and navigation icons on OLED black bars
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpendlyTypography,
        content = content
    )
}
