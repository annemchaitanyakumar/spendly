package com.spendly.app.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────
//  Pure OLED Black & Warm White Monochrome Palette
//  No colors: strictly OLED pitch black (#000000) and warm white (#FDFBF7)
// ─────────────────────────────────────────────────────────────────────

val OledBlack = Color(0xFF000000)
val OledSurface1 = Color(0xFF0A0A0A)
val OledSurface2 = Color(0xFF121212)
val OledSurface3 = Color(0xFF1A1A1A)
val OledSurface4 = Color(0xFF242424)
val OledBorder = Color(0xFF2C2C2C)
val OledBorderSubtle = Color(0xFF1F1F1F)

val WarmWhite = Color(0xFFFDFBF7)
val WarmWhiteHigh = Color(0xFFFFFFFF)
val WarmWhiteMuted = Color(0xFFA8A29E)
val WarmWhiteSubtle = Color(0xFF78716C)
val WarmWhiteDim = Color(0xFF52525B)

// Aliases mapped strictly to OLED Black and Warm White monochrome
val SpendlyGreen10 = OledSurface1
val SpendlyGreen20 = OledSurface2
val SpendlyGreen30 = OledSurface3
val SpendlyGreen40 = WarmWhite
val SpendlyGreen50 = WarmWhite
val SpendlyGreen60 = WarmWhite
val SpendlyGreen70 = WarmWhite
val SpendlyGreen80 = WarmWhite
val SpendlyGreen90 = OledSurface3
val SpendlyGreen95 = OledSurface2
val SpendlyGreen99 = OledSurface1

val SpendlyNeutral10 = OledBlack
val SpendlyNeutral20 = OledSurface1
val SpendlyNeutral30 = OledSurface2
val SpendlyNeutral40 = OledSurface3
val SpendlyNeutral50 = WarmWhiteDim
val SpendlyNeutral60 = WarmWhiteSubtle
val SpendlyNeutral70 = WarmWhiteMuted
val SpendlyNeutral80 = WarmWhiteMuted
val SpendlyNeutral87 = WarmWhite
val SpendlyNeutral90 = WarmWhite
val SpendlyNeutral95 = WarmWhite
val SpendlyNeutral99 = OledBlack

val SpendlyNeutralVariant30 = OledSurface2
val SpendlyNeutralVariant50 = OledBorder
val SpendlyNeutralVariant80 = WarmWhiteMuted
val SpendlyNeutralVariant90 = OledSurface3

// Error & Semantics — mapped strictly to Warm White and OLED contrast
val ErrorRed = WarmWhite
val ErrorRedContainer = OledSurface3
val ErrorRedDark = WarmWhite

val IncomeGreen = WarmWhite
val ExpenseRed = WarmWhiteMuted
val PendingAmber = WarmWhiteMuted
val RefundBlue = WarmWhite
