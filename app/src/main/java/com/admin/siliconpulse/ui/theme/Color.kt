package com.admin.siliconpulse.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- Fondo: obsidiana con un tinte azul muy leve, en degradado vertical.
val Ink = Color(0xFF070A12)
val InkDeep = Color(0xFF0B1020)
val Panel = Color(0xFF0F1626)
val PanelHigh = Color(0xFF161E31)
val Outline = Color(0xFF223049)
val OutlineSoft = Color(0x33FFFFFF)

val TextPrimary = Color(0xFFEAF0FF)
val TextMuted = Color(0xFF8A96B3)
val TextFaint = Color(0xFF55607A)

// --- Acentos neón.
val Cyan = Color(0xFF4CC9F0)
val Violet = Color(0xFF9B5DE5)
val Mint = Color(0xFF3DDC97)
val Amber = Color(0xFFF9C74F)
val Rose = Color(0xFFFF5C7A)
val Sky = Color(0xFF7DD3FC)

/** Colores fijos por eje para que X/Y/Z se lean igual en todas las graficas. */
val AxisX = Cyan
val AxisY = Amber
val AxisZ = Mint

val BackgroundBrush = Brush.verticalGradient(listOf(InkDeep, Ink))
val AccentBrush = Brush.linearGradient(listOf(Cyan, Violet))
val PanelBorderBrush = Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x0DFFFFFF), Color(0x26FFFFFF)))

fun glow(color: Color) = Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f)))
