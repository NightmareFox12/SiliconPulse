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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.admin.siliconpulse.ui.theme.AxisX
import com.admin.siliconpulse.ui.theme.AxisY
import com.admin.siliconpulse.ui.theme.AxisZ
import com.admin.siliconpulse.ui.theme.Ink
import com.admin.siliconpulse.ui.theme.Outline

/**
 * Grafica oscilografica. `frame` es un contador que el ViewModel incrementa una vez
 * por frame de pantalla; leerlo aqui es lo que dispara el redibujado.
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
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(200, 139, 152, 165)
            textSize = 26f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ink),
    ) {
        @Suppress("UNUSED_EXPRESSION") frame // dependencia explicita para recomponer

        val w = size.width
        val h = size.height
        val leftGutter = 92f
        val plotW = w - leftGutter - 8f

        drawGrid(leftGutter, plotW)

        val (min, max) = buffer.range(axes)
        val span = max - min
        fun yOf(v: Float) = h - ((v - min) / span) * h

        // Etiquetas de escala
        val nc = drawContext.canvas.nativeCanvas
        for (i in 0..4) {
            val v = max - span * i / 4f
            val yy = yOf(v).coerceIn(24f, h - 6f)
            nc.drawText(formatTick(v), 6f, yy, textPaint)
        }
        nc.drawText(unit, 6f, h - 8f, textPaint)

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
            drawPath(
                p,
                color = axisColor(axis),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // Cursor en el ultimo punto
        val lastV = buffer.xAt(n - 1)
        drawCircle(axisColor(0), radius = 4f, center = Offset(xStart + (n - 1) * step, yOf(lastV)))
    }
}

private fun DrawScope.drawGrid(left: Float, plotW: Float) {
    val h = size.height
    val cols = 10
    val rows = 4
    for (i in 0..cols) {
        val x = left + plotW * i / cols
        drawLine(Outline, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
    }
    for (i in 0..rows) {
        val y = h * i / rows
        drawLine(Outline, Offset(left, y), Offset(left + plotW, y), strokeWidth = 1f)
    }
    drawLine(Outline.copy(alpha = 0.8f), Offset(left, 0f), Offset(left, h), strokeWidth = 2f)
}

fun axisColor(axis: Int): Color = when (axis) { 0 -> AxisX; 1 -> AxisY; else -> AxisZ }

private fun formatTick(v: Float): String = when {
    kotlin.math.abs(v) >= 1000f -> "%.0f".format(v)
    kotlin.math.abs(v) >= 10f -> "%.1f".format(v)
    else -> "%.2f".format(v)
}
