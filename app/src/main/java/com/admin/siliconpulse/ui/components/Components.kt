package com.admin.siliconpulse.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.admin.siliconpulse.ui.theme.Cyan
import com.admin.siliconpulse.ui.theme.Mint
import com.admin.siliconpulse.ui.theme.Mono
import com.admin.siliconpulse.ui.theme.MonoValue
import com.admin.siliconpulse.ui.theme.Outline
import com.admin.siliconpulse.ui.theme.Panel
import com.admin.siliconpulse.ui.theme.PanelBorderBrush
import com.admin.siliconpulse.ui.theme.PanelHigh
import com.admin.siliconpulse.ui.theme.Rose
import com.admin.siliconpulse.ui.theme.TextFaint
import com.admin.siliconpulse.ui.theme.TextMuted

/** Tarjeta "vidrio": superficie con degradado sutil y borde luminoso de 1 px. */
@Composable
fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color = Cyan,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val dark = com.admin.siliconpulse.ui.theme.SPTheme.palette.isDark
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (dark) Modifier else Modifier.shadow(6.dp, shape, ambientColor = Color(0x1A0E1420), spotColor = Color(0x1A0E1420)))
            .clip(shape)
            .background(if (dark) Brush.verticalGradient(listOf(PanelHigh.copy(alpha = 0.9f), Panel)) else Brush.verticalGradient(listOf(Panel, Panel)))
            .border(1.dp, if (dark) PanelBorderBrush else Brush.verticalGradient(listOf(Outline, Outline)), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 16.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
fun KeyValue(key: String, value: String?, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = MaterialTheme.typography.bodyLarge, color = TextMuted, modifier = Modifier.weight(1f))
        Text(
            value ?: "—",
            style = MonoValue,
            color = if (value == null) TextFaint else valueColor,
            modifier = Modifier.weight(1.3f),
            textAlign = TextAlign.End,
        )
    }
}

/** Separador hairline entre grupos de KeyValue. */
@Composable
fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(1.dp)
            .background(Outline.copy(alpha = 0.6f)),
    )
}

@Composable
fun BigStat(
    label: String,
    value: String,
    unit: String = "",
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1, softWrap = false)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

/** Tile de metrica con fondo tenue del color de acento. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String = "",
    color: Color = Cyan,
    modifier: Modifier = Modifier,
    footer: String? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp, lineHeight = 24.sp), color = color, maxLines = 1, softWrap = false)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1, softWrap = false)
            }
        }
        if (footer != null) {
            Spacer(Modifier.height(2.dp))
            Text(footer, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
        }
    }
}

/** Indicador de estado tipo LED: verde = ok/inactivo, rojo = alerta/activo. */
@Composable
fun StatusPill(text: String, alert: Boolean, modifier: Modifier = Modifier, neutral: Boolean = false) {
    val color = when {
        neutral -> TextMuted
        alert -> Rose
        else -> Mint
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(7.dp)) {
            drawCircle(color.copy(alpha = 0.35f), radius = size.minDimension / 2 * 1.6f)
            drawCircle(color)
        }
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Dot(color: Color) {
    Canvas(Modifier.size(8.dp).clip(CircleShape)) { drawCircle(color) }
}

@Composable
fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * Medidor de arco (270 grados) con degradado y valor centrado.
 * `fraction` en 0..1. Animado para que las transiciones no salten.
 */
@Composable
fun Gauge(
    fraction: Float,
    value: String,
    label: String,
    color: Color = Cyan,
    modifier: Modifier = Modifier,
    size: Dp = 108.dp,
    unit: String = "",
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "gauge")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val start = 135f
            val sweepTotal = 270f
            drawArc(
                color = Outline,
                startAngle = start,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to color.copy(alpha = 0.55f),
                        0.75f to color,
                        1f to color,
                    ),
                    startAngle = start,
                    sweepAngle = sweepTotal * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1, softWrap = false)
                if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.padding(bottom = 5.dp, start = 2.dp))
            }
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

/**
 * Sparkline suave con relleno degradado. Pensada para historiales cortos (60-180 puntos).
 * Sin ejes: la lectura numerica va al lado, no encima.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color = Cyan,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    minOverride: Float? = null,
    maxOverride: Float? = null,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Canvas
        var min = minOverride ?: values.min()
        var max = maxOverride ?: values.max()
        if (max - min < 1e-3f) { min -= 0.5f; max += 0.5f }
        val span = max - min
        val w = size.width
        val h = size.height
        val stepX = w / (values.size - 1)
        fun pt(i: Int) = Offset(i * stepX, h - ((values[i] - min) / span) * (h - 6f) - 3f)

        val line = Path()
        val area = Path()
        var prev = pt(0)
        line.moveTo(prev.x, prev.y)
        area.moveTo(prev.x, h)
        area.lineTo(prev.x, prev.y)
        for (i in 1 until values.size) {
            val cur = pt(i)
            val cx = (prev.x + cur.x) / 2
            line.cubicTo(cx, prev.y, cx, cur.y, cur.x, cur.y)
            area.cubicTo(cx, prev.y, cx, cur.y, cur.x, cur.y)
            prev = cur
        }
        area.lineTo(prev.x, h)
        area.close()

        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
        drawPath(line, color.copy(alpha = 0.35f), style = Stroke(6f, cap = StrokeCap.Round))
        drawPath(line, color, style = Stroke(2.2f, cap = StrokeCap.Round))
        drawCircle(color, radius = 4f, center = prev)
        drawCircle(color.copy(alpha = 0.3f), radius = 9f, center = prev)
    }
}

/** Barra de progreso fina con degradado, para niveles 0..1. */
@Composable
fun LevelBar(fraction: Float, color: Color = Cyan, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "bar")
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        drawRoundRect(Outline, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
        if (animated > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color)),
                size = Size(size.width * animated, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height),
            )
        }
    }
}

/** Titulo de seccion en mayusculas con tipografia mono, para separar grupos dentro de una pantalla. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextFaint,
        fontFamily = Mono,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
    )
}

@Composable
fun TwoCol(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}
