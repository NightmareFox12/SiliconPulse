package com.admin.siliconpulse.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Paleta completa de la app. Hay una variante oscura (obsidiana + neon) y una clara.
 * Los nombres publicos de abajo (Ink, Panel, Cyan...) leen la paleta activa, que es un
 * estado de Compose: cualquier composable o Canvas que los use se redibuja al cambiar el tema.
 */
data class Palette(
    val ink: Color,
    val inkDeep: Color,
    val panel: Color,
    val panelHigh: Color,
    val outline: Color,
    val outlineSoft: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textFaint: Color,
    val cyan: Color,
    val violet: Color,
    val mint: Color,
    val amber: Color,
    val rose: Color,
    val sky: Color,
    val panelBorder: Brush,
    val isDark: Boolean,
)

val DarkPalette = Palette(
    ink = Color(0xFF070A12),
    inkDeep = Color(0xFF0B1020),
    panel = Color(0xFF0F1626),
    panelHigh = Color(0xFF161E31),
    outline = Color(0xFF223049),
    outlineSoft = Color(0x33FFFFFF),
    textPrimary = Color(0xFFEAF0FF),
    textMuted = Color(0xFF8A96B3),
    textFaint = Color(0xFF55607A),
    cyan = Color(0xFF4CC9F0),
    violet = Color(0xFF9B5DE5),
    mint = Color(0xFF3DDC97),
    amber = Color(0xFFF9C74F),
    rose = Color(0xFFFF5C7A),
    sky = Color(0xFF7DD3FC),
    panelBorder = Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x0DFFFFFF), Color(0x26FFFFFF))),
    isDark = true,
)

/** Clara: mismos acentos pero mas saturados y oscuros para contrastar sobre blanco. */
val LightPalette = Palette(
    ink = Color(0xFFEDF0F6),
    inkDeep = Color(0xFFF4F6FB),
    panel = Color(0xFFFFFFFF),
    panelHigh = Color(0xFFF6F8FC),
    outline = Color(0xFFD9E0EB),
    outlineSoft = Color(0x1A000000),
    textPrimary = Color(0xFF0E1420),
    textMuted = Color(0xFF55617A),
    textFaint = Color(0xFF8A94A8),
    cyan = Color(0xFF0A8FB8),
    violet = Color(0xFF6D3FD6),
    mint = Color(0xFF0E9F6E),
    amber = Color(0xFFC27A00),
    rose = Color(0xFFD92A4E),
    sky = Color(0xFF0369A1),
    panelBorder = Brush.linearGradient(listOf(Color(0x22000000), Color(0x0A000000), Color(0x14000000))),
    isDark = false,
)

object SPTheme {
    var palette: Palette by mutableStateOf(DarkPalette)
}

// --- Nombres publicos usados en toda la UI. Leen la paleta activa.
val Ink: Color get() = SPTheme.palette.ink
val InkDeep: Color get() = SPTheme.palette.inkDeep
val Panel: Color get() = SPTheme.palette.panel
val PanelHigh: Color get() = SPTheme.palette.panelHigh
val Outline: Color get() = SPTheme.palette.outline
val OutlineSoft: Color get() = SPTheme.palette.outlineSoft
val TextPrimary: Color get() = SPTheme.palette.textPrimary
val TextMuted: Color get() = SPTheme.palette.textMuted
val TextFaint: Color get() = SPTheme.palette.textFaint
val Cyan: Color get() = SPTheme.palette.cyan
val Violet: Color get() = SPTheme.palette.violet
val Mint: Color get() = SPTheme.palette.mint
val Amber: Color get() = SPTheme.palette.amber
val Rose: Color get() = SPTheme.palette.rose
val Sky: Color get() = SPTheme.palette.sky

/** Colores fijos por eje para que X/Y/Z se lean igual en todas las graficas. */
val AxisX: Color get() = Cyan
val AxisY: Color get() = Amber
val AxisZ: Color get() = Mint

val BackgroundBrush: Brush get() = Brush.verticalGradient(listOf(InkDeep, Ink))
val AccentBrush: Brush get() = Brush.linearGradient(listOf(Cyan, Violet))
val PanelBorderBrush: Brush get() = SPTheme.palette.panelBorder

fun glow(color: Color) = Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f)))
