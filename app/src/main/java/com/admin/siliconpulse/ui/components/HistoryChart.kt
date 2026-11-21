package com.admin.siliconpulse.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.admin.siliconpulse.ui.theme.Cyan
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
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shader.verticalGradient
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider

/**
 * Historial de una o varias series sobre Vico: linea suave, area con degradado,
 * eje izquierdo minimo y sin eje inferior (el tiempo es "los ultimos N segundos").
 *
 * El rango Y se cine a los datos (con 10 % de margen) salvo que se fije `minY`/`maxY`,
 * para que una senal de -98 dBm no se dibuje aplastada contra un cero inutil.
 */
@Composable
fun HistoryChart(
    series: List<List<Float>>,
    colors: List<Color> = listOf(Cyan),
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    minY: Float? = null,
    maxY: Float? = null,
) {
    val producer = remember { CartesianChartModelProducer() }
    val usable = series.filter { it.size >= 2 }
    LaunchedEffect(series) {
        if (usable.isEmpty()) return@LaunchedEffect
        producer.runTransaction {
            lineSeries { usable.forEach { s -> series(s.indices.map { it.toFloat() }, s) } }
        }
    }

    val all = usable.flatten()
    val dataMin = all.minOrNull() ?: 0f
    val dataMax = all.maxOrNull() ?: 1f
    val pad = ((dataMax - dataMin).takeIf { it > 1e-3f } ?: 1f) * 0.12f
    val lo = (minY ?: (dataMin - pad)).toDouble()
    val hi = (maxY ?: (dataMax + pad)).toDouble()
    val rangeProvider = remember(lo, hi) { CartesianLayerRangeProvider.fixed(minY = lo, maxY = hi) }

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
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(lines),
                rangeProvider = rangeProvider,
            ),
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
