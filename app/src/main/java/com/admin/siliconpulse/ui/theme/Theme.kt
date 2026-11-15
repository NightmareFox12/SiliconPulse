package com.admin.siliconpulse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private fun Palette.toScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = cyan,
        onPrimary = ink,
        primaryContainer = cyan.copy(alpha = 0.18f),
        onPrimaryContainer = cyan,
        secondary = violet,
        onSecondary = ink,
        secondaryContainer = violet.copy(alpha = 0.22f),
        onSecondaryContainer = textPrimary,
        tertiary = mint,
        onTertiary = ink,
        error = rose,
        onError = ink,
        background = ink,
        onBackground = textPrimary,
        surface = panel,
        onSurface = textPrimary,
        surfaceVariant = panelHigh,
        onSurfaceVariant = textMuted,
        outline = outline,
        outlineVariant = outline,
        surfaceContainer = panel,
        surfaceContainerHigh = panelHigh,
        surfaceContainerHighest = panelHigh,
        surfaceContainerLow = inkDeep,
        surfaceContainerLowest = ink,
    )
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Sigue el modo claro/oscuro del sistema. Sin color dinamico: las trazas deben leerse igual en cualquier equipo. */
@Composable
fun SiliconPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    if (SPTheme.palette !== palette) SPTheme.palette = palette
    MaterialTheme(
        colorScheme = palette.toScheme(),
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
