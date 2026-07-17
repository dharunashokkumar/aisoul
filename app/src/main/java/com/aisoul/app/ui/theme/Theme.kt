package com.aisoul.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val ColorScheme = darkColorScheme(
    primary = AccentIce,
    onPrimary = TextInverse,
    background = Surface0,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = BorderSubtle,
    outlineVariant = Divider,
    error = Negative,
    onError = TextInverse,
    secondary = TextSecondary,
    onSecondary = TextInverse,
    tertiary = TextTertiary,
    onTertiary = TextInverse,
)

@Composable
fun AiSoulTheme(content: @Composable () -> Unit) {
    val type = AiSoulTypography()
    // map the 7 roles onto M3 slots so M3 components inherit the system
    val m3Typography = Typography(
        displayLarge = type.display,
        displayMedium = type.display,
        headlineLarge = type.headline,
        headlineMedium = type.headline,
        titleLarge = type.title,
        titleMedium = type.title,
        titleSmall = type.title,
        bodyLarge = type.body,
        bodyMedium = type.body,
        bodySmall = type.caption,
        labelLarge = type.body,
        labelMedium = type.caption,
        labelSmall = type.overline,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(14.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
    CompositionLocalProvider(LocalAiSoulTypography provides type) {
        MaterialTheme(
            colorScheme = ColorScheme,
            typography = m3Typography,
            shapes = shapes,
            content = content,
        )
    }
}
