package com.spendly.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────────────
//  Light color scheme
// ─────────────────────────────────────────────────────────────────────

private val LightColorScheme = lightColorScheme(
    primary = SpendlyGreen40,
    onPrimary = Color.White,
    primaryContainer = SpendlyGreen90,
    onPrimaryContainer = SpendlyGreen10,
    secondary = SpendlyNeutralVariant50,
    onSecondary = Color.White,
    secondaryContainer = SpendlyNeutralVariant90,
    onSecondaryContainer = SpendlyNeutralVariant30,
    tertiary = SpendlyGreen60,
    onTertiary = Color.White,
    background = SpendlyNeutral99,
    onBackground = SpendlyNeutral10,
    surface = SpendlyNeutral99,
    onSurface = SpendlyNeutral10,
    surfaceVariant = SpendlyNeutralVariant90,
    onSurfaceVariant = SpendlyNeutralVariant30,
    surfaceContainer = SpendlyNeutral95,
    surfaceContainerHigh = SpendlyNeutral90,
    outline = SpendlyNeutralVariant50,
    outlineVariant = SpendlyNeutralVariant80,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedContainer,
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = SpendlyNeutral20,
    inverseOnSurface = SpendlyNeutral95,
    inversePrimary = SpendlyGreen80,
    scrim = Color.Black
)

// ─────────────────────────────────────────────────────────────────────
//  Dark color scheme
// ─────────────────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary = SpendlyGreen80,
    onPrimary = SpendlyGreen20,
    primaryContainer = SpendlyGreen30,
    onPrimaryContainer = SpendlyGreen90,
    secondary = SpendlyNeutralVariant80,
    onSecondary = SpendlyNeutralVariant30,
    secondaryContainer = SpendlyNeutralVariant50,
    onSecondaryContainer = SpendlyNeutralVariant90,
    tertiary = SpendlyGreen70,
    onTertiary = SpendlyGreen10,
    background = SpendlyNeutral10,
    onBackground = SpendlyNeutral90,
    surface = SpendlyNeutral10,
    onSurface = SpendlyNeutral90,
    surfaceVariant = SpendlyNeutralVariant30,
    onSurfaceVariant = SpendlyNeutralVariant80,
    surfaceContainer = SpendlyNeutral20,
    surfaceContainerHigh = SpendlyNeutral30,
    outline = SpendlyNeutralVariant50,
    outlineVariant = SpendlyNeutralVariant30,
    error = ErrorRedDark,
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = ErrorRedDark,
    inverseSurface = SpendlyNeutral90,
    inverseOnSurface = SpendlyNeutral20,
    inversePrimary = SpendlyGreen40,
    scrim = Color.Black
)

// ─────────────────────────────────────────────────────────────────────
//  Theme composable
// ─────────────────────────────────────────────────────────────────────

@Composable
fun SpendlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpendlyTypography,
        content = content
    )
}
