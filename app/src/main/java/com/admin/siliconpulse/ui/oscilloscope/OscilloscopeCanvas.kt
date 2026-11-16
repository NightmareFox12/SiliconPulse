package com.admin.siliconpulse.ui.oscilloscope

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.admin.siliconpulse.ui.theme.AxisX
import com.admin.siliconpulse.ui.theme.AxisY
import com.admin.siliconpulse.ui.theme.AxisZ
import com.admin.siliconpulse.ui.theme.Ink
import com.admin.siliconpulse.ui.theme.InkDeep
import com.admin.siliconpulse.ui.theme.Outline
import com.admin.siliconpulse.ui.theme.TextMuted

/**
 * Grafica oscilografica. `frame` es un contador que el ViewModel incrementa una vez
 * por frame de pantalla; leerlo aqui es lo que dispara el redibujado.
 *
 * Estetica: sin caja, sin rejilla vertical. Cuatro guias horizontales punteadas,
 * trazas con halo suave y un area degradada bajo la primera traza cuando hay un solo eje.
 */
@Composable
fun OscilloscopeCanvas(
    buffer: TraceBuffer,
    axes: Int,
    frame: Int,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val paths = remember { Array(3) { Path() } }
    val area = remember { Path() }
    val labelColor = TextMuted
    val textPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(230, (labelColor.red * 255).toInt(), (labelColor.green * 255).toInt(), (labelColor.blue * 255).toInt())
            textSize = 22f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
    }
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 10f)) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(InkDeep, Ink))),
    ) {
        @Suppress("UNUSED_EXPRESSION") frame

        val w = size.width
        val h = size.height
        val padTop = 18f
        val padBottom = 14f
        val leftGutter = 98f
        val plotW = w - leftGutter - 14f
        val plotH = h - padTop - padBottom

        val (min, max) = buffer.range(axes)
        val span = max - min
        fun yOf(v: Float) = padTop + plotH - ((v - min) / span) * plotH

        // Guias horizontales + etiquetas
        val nc = drawContext.canvas.nativeCanvas
        for (i in 0..4) {
            val v = max - span * i / 4f
            val y = yOf(v)
            drawLine(Outline.copy(alpha = 0.7f), Offset(leftGutter, y), Offset(leftGutter + plotW, y), strokeWidth = 1f, pathEffect = dash)
            nc.drawText(formatTick(v), leftGutter - 12f, y + 8f, textPaint)
        }

        val n = buffer.size
        if (n < 2) return@Canvas
        val step = plotW / (buffer.capacity - 1)
        val xStart = leftGutter + (buffer.capacity - n) * step

        for (axis in 0 until axes) {
            val p = paths[axis]
            p.reset()
            for (i in 0 until n) {
                val v = when (axis) { 0 -> buffer.xAt(i); 1 -> buffer.yAt(i); else -> buffer.zAt(i) }
                val px = xStart + i * step
                val py = yOf(v)
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            val c = axisColor(axis)
            if (axes == 1) {
                area.reset()
                area.addPath(p)
                area.lineTo(xStart + (n - 1) * step, padTop + plotH)
                area.lineTo(xStart, padTop + plotH)
                area.close()
                drawPath(area, Brush.verticalGradient(listOf(c.copy(alpha = 0.30f), c.copy(alpha = 0f)), startY = padTop, endY = padTop + plotH))
            }
            drawPath(p, c.copy(alpha = 0.22f), style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(p, c, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Cursor en el ultimo punto del primer eje
        val last = Offset(xStart + (n - 1) * step, yOf(buffer.xAt(n - 1)))
        drawCircle(axisColor(0).copy(alpha = 0.25f), radius = 10f, center = last)
        drawCircle(axisColor(0), radius = 3.5f, center = last)
    }
}

fun axisColor(axis: Int): Color = when (axis) { 0 -> AxisX; 1 -> AxisY; else -> AxisZ }

private fun formatTick(v: Float): String = when {
    kotlin.math.abs(v) >= 1000f -> "%.0f".format(v)
    kotlin.math.abs(v) >= 10f -> "%.1f".format(v)
    else -> "%.2f".format(v)
}
