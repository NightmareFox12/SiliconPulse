package com.admin.siliconpulse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val EngineeringDark = darkColorScheme(
    primary = Cyan,
    onPrimary = Ink,
    secondary = Amber,
    onSecondary = Ink,
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
    surfaceContainerLow = Ink,
)

/** Dashboard de ingenieria: siempre oscuro, sin color dinamico, para que las trazas se lean igual en cualquier equipo. */
@Composable
fun SiliconPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EngineeringDark,
        typography = Typography,
        content = content,
    )
}
