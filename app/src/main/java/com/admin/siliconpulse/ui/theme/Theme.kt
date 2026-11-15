package com.admin.siliconpulse.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val EngineeringDark = darkColorScheme(
    primary = Cyan,
    onPrimary = Ink,
    primaryContainer = Cyan.copy(alpha = 0.18f),
    onPrimaryContainer = Cyan,
    secondary = Violet,
    onSecondary = Ink,
    secondaryContainer = Violet.copy(alpha = 0.22f),
    onSecondaryContainer = TextPrimary,
    tertiary = Mint,
    onTertiary = Ink,
    error = Rose,
    onError = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = TextMuted,
    outline = Outline,
    outlineVariant = Outline,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    surfaceContainerHighest = PanelHigh,
    surfaceContainerLow = InkDeep,
    surfaceContainerLowest = Ink,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Dashboard de ingenieria: siempre oscuro, sin color dinamico, para que las trazas se lean igual en cualquier equipo. */
@Composable
fun SiliconPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EngineeringDark,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
