package com.admin.siliconpulse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Mono = FontFamily.Monospace
val Sans = FontFamily.SansSerif

/**
 * Escala legible: cuerpo 15-16 sp, etiquetas 13 sp, nunca por debajo de 12 sp.
 * Mono se reserva para valores numericos; todo el texto corrido va en sans.
 */
val Typography = Typography(
    displaySmall = TextStyle(fontFamily = Mono, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontFamily = Mono, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 13.5.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontSize = 12.5.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold),
)
