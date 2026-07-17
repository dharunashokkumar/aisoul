package com.aisoul.app.ui.theme

import androidx.compose.ui.graphics.Color

// DESIGN.md §1 — every color in the app comes from here. No literals in screens.

// Surfaces — elevation via lightness, never shadows
val Surface0 = Color(0xFF0B0B0D)
val Surface1 = Color(0xFF131316)
val Surface2 = Color(0xFF1B1B20)
val Surface3 = Color(0xFF242429)

// Borders & dividers
val BorderSubtle = Color(1f, 1f, 1f, 0.06f)
val BorderStrong = Color(1f, 1f, 1f, 0.12f)
val Divider = Color(1f, 1f, 1f, 0.04f)

// Text
val TextPrimary = Color(1f, 1f, 1f, 0.92f)
val TextSecondary = Color(1f, 1f, 1f, 0.60f)
val TextTertiary = Color(1f, 1f, 1f, 0.38f)
val TextInverse = Color(0xFF0B0B0D)

// Accents — one per app; ice is aisoul's (SPEC §2)
val AccentIce = Color(0xFF8FB8C9)
val AccentBrass = Color(0xFFC9A961)
val AccentSage = Color(0xFF9CB89C)

// Semantic — dusty, muted, adult
val Positive = Color(0xFF7FB58A)
val Negative = Color(0xFFC97B6E)
val Warning = Color(0xFFC9A961)

// Bottom sheet grabber
val Grabber = Color(1f, 1f, 1f, 0.15f)

// Scrim behind sheets
val Scrim = Color(0f, 0f, 0f, 0.6f)
