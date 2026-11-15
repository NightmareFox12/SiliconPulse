package com.admin.siliconpulse.ui.oscilloscope

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.admin.siliconpulse.domain.model.SamplingRate
import com.admin.siliconpulse.domain.model.SensorChannel
import com.admin.siliconpulse.ui.components.Dot
import com.admin.siliconpulse.ui.theme.Mono
import com.admin.siliconpulse.ui.components.KeyValue
import com.admin.siliconpulse.ui.components.Note
import com.admin.siliconpulse.ui.components.Panel
import com.admin.siliconpulse.ui.theme.TextMuted

@Composable
fun OscilloscopeScreen(vm: OscilloscopeViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showSensorList by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SensorChannel.entries.forEach { ch ->
                    val available = ui.available[ch] == true
                    FilterChip(
                        selected = ui.channel == ch,
                        onClick = { if (available) vm.select(ch) },
                        label = { Text(ch.label) },
                        enabled = available,
                    )
                }
            }
        }
        item {
            Panel(
                title = ui.channel.label,
                trailing = {
                    TextButton(onClick = vm::togglePause) { Text(if (ui.paused) "Reanudar" else "Pausar") }
                },
            ) {
                OscilloscopeCanvas(buffer = vm.buffer, axes = ui.channel.axes, frame = vm.frame, unit = ui.channel.unit)
                Spacer(Modifier.height(10.dp))
                LiveValues(vm, ui)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Tasa", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    SamplingRate.entries.forEach { r ->
                        FilterChip(selected = ui.rate == r, onClick = { vm.setRate(r) }, label = { Text(r.label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                ui.error?.let { Note("Error: $it") }
                Note("Buffer de ${vm.buffer.capacity} muestras. Listener en hilo dedicado; redibujo limitado a 60 fps.")
            }
        }
        item {
            Panel(
                title = "Sensores reportados por el HAL (${ui.sensors.size})",
                trailing = { TextButton(onClick = { showSensorList = !showSensorList }) { Text(if (showSensorList) "Ocultar" else "Ver") } },
            ) {
                if (!showSensorList) Text("Lista completa con vendor, rango, resolucion y consumo.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        if (showSensorList) {
            items(ui.sensors) { s ->
                Panel(title = s.typeName.removePrefix("android.sensor.")) {
                    KeyValue("Nombre", s.name)
                    KeyValue("Vendor", s.vendor)
                    KeyValue("Rango max", "%.3g".format(s.maxRange))
                    KeyValue("Resolucion", "%.3g".format(s.resolution))
                    KeyValue("Consumo", "%.2f mA".format(s.powerMa))
                    KeyValue("Delay min/max", "${s.minDelayUs} / ${s.maxDelayUs} µs")
                    KeyValue("Modo", s.reportingMode + if (s.isWakeUp) " · wake-up" else "")
                }
            }
        }
    }
}

@Composable
private fun LiveValues(vm: OscilloscopeViewModel, ui: OscilloscopeUiState) {
    @Suppress("UNUSED_EXPRESSION") vm.frame
    val b = vm.buffer
    val axes = ui.channel.axes
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AxisValue("X", b.lastX, 0, Modifier.weight(1.1f))
            if (axes > 1) AxisValue("Y", b.lastY, 1, Modifier.weight(1.1f))
            if (axes > 2) AxisValue("Z", b.lastZ, 2, Modifier.weight(1.1f))
            Column(Modifier.weight(0.7f)) {
                Text("Hz", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Text("%.0f".format(ui.sampleRateHz), style = MaterialTheme.typography.titleMedium, fontFamily = Mono, maxLines = 1)
            }
        }
        if (ui.channel == SensorChannel.PRESSURE && b.lastX > 0f) {
            val alt = 44330f * (1f - Math.pow((b.lastX / 1013.25f).toDouble(), 0.1903).toFloat())
            Spacer(Modifier.height(6.dp))
            KeyValue("Altitud estimada (ISA)", "%.1f m".format(alt))
        }
        if (ui.channel == SensorChannel.MAGNETOMETER) {
            val mag = kotlin.math.sqrt(b.lastX * b.lastX + b.lastY * b.lastY + b.lastZ * b.lastZ)
            Spacer(Modifier.height(6.dp))
            KeyValue("|B| total", "%.1f µT".format(mag))
        }
        if (ui.channel == SensorChannel.ACCELEROMETER) {
            val g = kotlin.math.sqrt(b.lastX * b.lastX + b.lastY * b.lastY + b.lastZ * b.lastZ) / 9.80665f
            Spacer(Modifier.height(6.dp))
            KeyValue("|a| en g", "%.3f g".format(g))
        }
        Spacer(Modifier.height(4.dp))
        KeyValue("Precision reportada", when (ui.accuracy) { 3 -> "alta"; 2 -> "media"; 1 -> "baja"; 0 -> "no confiable"; else -> "n/a" })
    }
}

@Composable
private fun AxisValue(label: String, value: Float, axis: Int, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(axisColor(axis))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Text(
            "%.2f".format(value),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = Mono,
            color = axisColor(axis),
            maxLines = 1,
            softWrap = false,
        )
    }
}
