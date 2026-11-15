package com.admin.siliconpulse.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.admin.siliconpulse.ui.theme.Cyan
import com.admin.siliconpulse.ui.theme.Mono
import com.admin.siliconpulse.ui.theme.Outline
import com.admin.siliconpulse.ui.theme.TextMuted
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shader.verticalGradient
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider

/**
 * Historial de una o varias series sobre Vico: linea suave, area con degradado,
 * eje izquierdo minimo y sin eje inferior (el tiempo es "los ultimos N segundos").
 */
@Composable
fun HistoryChart(
    series: List<List<Float>>,
    colors: List<Color> = listOf(Cyan),
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(series) {
        val usable = series.filter { it.size >= 2 }
        if (usable.isEmpty()) return@LaunchedEffect
        producer.runTransaction {
            lineSeries { usable.forEach { s -> series(s.indices.map { it.toFloat() }, s) } }
        }
    }

    val lines = colors.map { c ->
        LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill(c)),
            areaFill = LineCartesianLayer.AreaFill.single(
                fill(ShaderProvider.verticalGradient(arrayOf(c.copy(alpha = 0.35f), c.copy(alpha = 0f)))),
            ),
        )
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(LineCartesianLayer.LineProvider.series(lines)),
            startAxis = VerticalAxis.rememberStart(
                line = rememberLineComponent(fill(Color.Transparent)),
                label = rememberAxisLabelComponent(color = TextMuted, typeface = android.graphics.Typeface.MONOSPACE),
                guideline = rememberAxisGuidelineComponent(fill = fill(Outline.copy(alpha = 0.6f)), thickness = 1.dp),
                tick = rememberLineComponent(fill(Color.Transparent)),
                itemPlacer = VerticalAxis.ItemPlacer.count({ 4 }),
            ),
        ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(height),
    )
}
