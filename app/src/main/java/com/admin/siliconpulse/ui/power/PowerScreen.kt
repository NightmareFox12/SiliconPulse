package com.admin.siliconpulse.ui.power

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.admin.siliconpulse.data.power.SleepTracker
import com.admin.siliconpulse.data.power.SleepWatchService
import com.admin.siliconpulse.domain.model.PowerState
import com.admin.siliconpulse.domain.repository.PowerRepository
import com.admin.siliconpulse.ui.components.Gauge
import com.admin.siliconpulse.ui.components.Hairline
import com.admin.siliconpulse.ui.components.HistoryChart
import com.admin.siliconpulse.ui.components.KeyValue
import com.admin.siliconpulse.ui.components.LevelBar
import com.admin.siliconpulse.ui.components.MetricTile
import com.admin.siliconpulse.ui.components.Note
import com.admin.siliconpulse.ui.components.Panel
import com.admin.siliconpulse.ui.components.SectionLabel
import com.admin.siliconpulse.ui.components.Sparkline
import com.admin.siliconpulse.ui.components.StatusPill
import com.admin.siliconpulse.ui.components.TwoCol
import com.admin.siliconpulse.ui.theme.Amber
import com.admin.siliconpulse.ui.theme.Cyan
import com.admin.siliconpulse.ui.theme.Ink
import com.admin.siliconpulse.ui.theme.Mint
import com.admin.siliconpulse.ui.theme.Rose
import com.admin.siliconpulse.ui.theme.TextMuted
import com.admin.siliconpulse.ui.theme.Violet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class PowerHistory(
    val currentMa: List<Float> = emptyList(),
    val temperatureC: List<Float> = emptyList(),
    val freeMemMb: List<Float> = emptyList(),
)

class PowerViewModel(repo: PowerRepository) : ViewModel() {
    private val _history = MutableStateFlow(PowerHistory())
    val history: StateFlow<PowerHistory> = _history

    val state: StateFlow<PowerState> = repo.observe()
        .onEach { s ->
            _history.update { h ->
                PowerHistory(
                    currentMa = (h.currentMa + (s.battery.currentNowMa ?: 0f)).takeLast(HISTORY),
                    temperatureC = (h.temperatureC + (s.battery.temperatureC ?: 0f)).takeLast(HISTORY),
                    freeMemMb = (h.freeMemMb + s.memory.availableMb.toFloat()).takeLast(HISTORY),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PowerState())

    val monitorRunning = MutableStateFlow(false)

    fun toggleMonitor(context: Context) {
        if (monitorRunning.value) SleepWatchService.stop(context) else SleepWatchService.start(context)
        monitorRunning.value = !monitorRunning.value
    }

    private companion object { const val HISTORY = 180 }
}

@Composable
fun PowerScreen(vm: PowerViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val h by vm.history.collectAsStateWithLifecycle()
    val running by vm.monitorRunning.collectAsStateWithLifecycle()
    val tracker by SleepTracker.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val b = s.battery
            Panel("Bateria", subtitle = "${b.technology ?: "—"} · ${b.plugged} · salud ${b.health}", trailing = { StatusPill(b.status.uppercase(), alert = b.health !in setOf("good", "unknown"), neutral = b.status == "unknown") }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Gauge(
                        fraction = (b.levelPercent.coerceAtLeast(0)) / 100f,
                        value = if (b.levelPercent >= 0) "${b.levelPercent}" else "—",
                        unit = "%",
                        label = "carga",
                        color = if (b.levelPercent in 0..15) Rose else Cyan,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Corriente", b.currentNowMa?.let { "%.0f".format(it) } ?: "—", "mA", currentColor(b.currentNowMa), footer = b.currentAverageMa?.let { "prom %.0f mA".format(it) })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile("Temp", b.temperatureC?.let { "%.1f".format(it) } ?: "—", "°C", tempColor(b.temperatureC), Modifier.weight(1f))
                            MetricTile("Volt", b.voltageMv?.let { "%.2f".format(it / 1000f) } ?: "—", "V", Violet, Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel("Corriente · ultimos ${h.currentMa.size} s")
                HistoryChart(series = listOf(h.currentMa), colors = listOf(currentColor(b.currentNowMa)), height = 140.dp)
                Hairline()
                KeyValue("Potencia instantanea", if (b.currentNowMa != null && b.voltageMv != null) "%.2f W".format(kotlin.math.abs(b.currentNowMa) * b.voltageMv / 1_000_000f) else null)
                KeyValue("Carga restante (fuel gauge)", b.chargeCounterMah?.let { "%.0f mAh".format(it) })
                KeyValue("Ciclos", b.cycleCount?.toString())
                KeyValue("Tiempo a carga completa", b.chargingTimeRemainingMin?.let { "$it min" })
                KeyValue("Ahorro de bateria", if (b.isBatterySaver) "activo" else "inactivo")
                Note("Signo segun el fuel gauge del OEM: positivo suele ser carga entrante, negativo consumo.")
            }
        }
        item {
            val sl = s.sleep
            val pct = sl.deepSleepPercentLastWindow
            Panel("Deep sleep", subtitle = "elapsedRealtime − uptimeMillis", accent = Violet, trailing = { StatusPill(if (running) "MONITOR ON" else "MONITOR OFF", alert = false, neutral = !running) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Gauge(
                        fraction = (pct ?: 0f) / 100f,
                        value = pct?.let { "%.0f".format(it) } ?: "—",
                        unit = "%",
                        label = "ventana",
                        color = sleepColor(pct),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sl.verdict, style = MaterialTheme.typography.titleSmall, color = sleepColor(pct))
                        Spacer(Modifier.height(8.dp))
                        Text("DESDE BOOT", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(Modifier.height(4.dp))
                        LevelBar(sl.deepSleepPercentSinceBoot / 100f, Violet)
                        Spacer(Modifier.height(4.dp))
                        Text("%.0f %% · suspend %s de %s".format(sl.deepSleepPercentSinceBoot, formatDuration(sl.deepSleepTotalMs), formatDuration(sl.elapsedRealtimeMs)), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (running) OutlinedButton(onClick = { vm.toggleMonitor(ctx) }) { Text("Detener monitor") }
                    else Button(onClick = { vm.toggleMonitor(ctx) }, colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Ink)) { Text("Iniciar monitor") }
                }
                Hairline()
                KeyValue("Pantalla", if (sl.screenOn) "encendida" else "apagada")
                KeyValue("Doze (device idle)", if (sl.deviceIdleMode) "si" else "no")
                KeyValue("Light idle", if (sl.lightIdleMode) "si" else "no")
                KeyValue("Exenta de optimizacion", if (sl.ignoringBatteryOptimizations) "si" else "no")
                if (tracker.history.isNotEmpty()) {
                    Hairline()
                    SectionLabel("Ventanas recientes")
                    tracker.history.takeLast(6).reversed().forEach { w ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDuration(w.durationMs), style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.width(64.dp))
                            Box(Modifier.weight(1f)) { LevelBar(w.sleepPercent / 100f, sleepColor(w.sleepPercent)) }
                            Spacer(Modifier.width(10.dp))
                            Text("%.0f %%".format(w.sleepPercent), style = MaterialTheme.typography.labelMedium, color = sleepColor(w.sleepPercent))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Note("El servicio no retiene WakeLock para no contaminar la medida. Desconecta el USB, apaga la pantalla unos minutos y vuelve.")
            }
        }
        item {
            val t = s.thermal
            val c = s.cpu
            Panel("Termico y SoC", subtitle = listOfNotNull(c.socManufacturer, c.socModel, c.abi).joinToString(" · ").ifBlank { "—" }, accent = Amber, trailing = { StatusPill(t.status.uppercase(), alert = t.statusCode >= 3, neutral = t.statusCode == 0) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Gauge(
                        fraction = t.headroom ?: 0f,
                        value = t.headroom?.let { "%.2f".format(it) } ?: "—",
                        label = "headroom",
                        color = when { t.headroom == null -> Cyan; t.headroom >= 0.95f -> Rose; t.headroom >= 0.8f -> Amber; else -> Mint },
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Temperatura bateria")
                        Sparkline(h.temperatureC, tempColor(s.battery.temperatureC), height = 48.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("1.0 = umbral de throttling severo", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                if (c.currentFreqMhz.any { it > 0 }) {
                    Hairline()
                    SectionLabel("Frecuencia por core")
                    c.currentFreqMhz.forEachIndexed { i, f ->
                        val max = c.maxFreqMhz.getOrNull(i) ?: 0
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("cpu$i", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.width(44.dp))
                            Box(Modifier.weight(1f)) { LevelBar(if (max > 0) f / max.toFloat() else 0f, if (max > 0 && f > max * 0.85) Amber else Cyan) }
                            Spacer(Modifier.width(10.dp))
                            Text(if (f > 0) "$f" else "off", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Note("MHz actuales sobre el maximo de cada core, leidos de sysfs.")
                } else {
                    Note("Este kernel no expone cpufreq a apps de usuario (SELinux).")
                }
            }
        }
        item {
            val m = s.memory
            Panel("Memoria", subtitle = "${m.totalMb} MB totales", accent = Mint, trailing = { if (m.lowMemory) StatusPill("LOW MEM", alert = true) }) {
                TwoCol(
                    left = { MetricTile("Libre", m.availableMb.toString(), "MB", if (m.lowMemory) Rose else Mint) },
                    right = { MetricTile("Esta app", m.appPssMb.toString(), "MB PSS", Cyan) },
                )
                Spacer(Modifier.height(10.dp))
                LevelBar(1f - m.availableMb / m.totalMb.coerceAtLeast(1).toFloat(), Mint)
                Spacer(Modifier.height(8.dp))
                Sparkline(h.freeMemMb, Mint, height = 40.dp)
                KeyValue("Umbral low-memory", "${m.thresholdMb} MB")
            }
        }
    }
}

private fun sleepColor(pct: Float?) = when {
    pct == null -> Violet
    pct >= 80f -> Mint
    pct >= 40f -> Amber
    else -> Rose
}

private fun currentColor(ma: Float?) = when {
    ma == null -> Cyan
    ma > 0 -> Mint
    ma < -1500 -> Rose
    ma < -600 -> Amber
    else -> Cyan
}

private fun tempColor(c: Float?) = when {
    c == null -> Cyan
    c >= 43f -> Rose
    c >= 38f -> Amber
    else -> Mint
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%dh %02dm".format(h, m) else if (m > 0) "%dm %02ds".format(m, sec) else "${sec}s"
}
