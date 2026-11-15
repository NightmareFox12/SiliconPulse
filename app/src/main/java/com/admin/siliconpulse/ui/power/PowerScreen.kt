package com.admin.siliconpulse.ui.power

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.admin.siliconpulse.ui.components.BigStat
import com.admin.siliconpulse.ui.components.KeyValue
import com.admin.siliconpulse.ui.components.Note
import com.admin.siliconpulse.ui.components.Panel
import com.admin.siliconpulse.ui.components.StatusPill
import com.admin.siliconpulse.ui.theme.Amber
import com.admin.siliconpulse.ui.theme.Cyan
import com.admin.siliconpulse.ui.theme.Mint
import com.admin.siliconpulse.ui.theme.Rose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PowerViewModel(repo: PowerRepository) : ViewModel() {
    val state: StateFlow<PowerState> = repo.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PowerState())

    val monitorRunning = MutableStateFlow(false)

    fun toggleMonitor(context: Context) {
        if (monitorRunning.value) SleepWatchService.stop(context) else SleepWatchService.start(context)
        monitorRunning.value = !monitorRunning.value
    }
}

@Composable
fun PowerScreen(vm: PowerViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val running by vm.monitorRunning.collectAsStateWithLifecycle()
    val tracker by SleepTracker.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val b = s.battery
            Panel("Bateria", trailing = { StatusPill(b.status.uppercase(), alert = b.health !in setOf("good", "unknown"), neutral = b.status == "unknown") }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BigStat("Carga", if (b.levelPercent >= 0) "${b.levelPercent}" else "—", "%", color = Cyan)
                    BigStat("Corriente", b.currentNowMa?.let { "%.0f".format(it) } ?: "—", "mA", color = currentColor(b.currentNowMa))
                    BigStat("Temp", b.temperatureC?.let { "%.1f".format(it) } ?: "—", "°C", color = tempColor(b.temperatureC))
                    BigStat("Voltaje", b.voltageMv?.let { "%.2f".format(it / 1000f) } ?: "—", "V")
                }
                Spacer(Modifier.height(8.dp))
                KeyValue("Corriente promedio", b.currentAverageMa?.let { "%.0f mA".format(it) })
                KeyValue("Potencia instantanea", if (b.currentNowMa != null && b.voltageMv != null) "%.2f W".format(kotlin.math.abs(b.currentNowMa) * b.voltageMv / 1_000_000f) else null)
                KeyValue("Carga restante (fuel gauge)", b.chargeCounterMah?.let { "%.0f mAh".format(it) })
                KeyValue("Fuente", b.plugged)
                KeyValue("Salud", b.health)
                KeyValue("Tecnologia", b.technology)
                KeyValue("Ciclos", b.cycleCount?.toString())
                KeyValue("Tiempo a carga completa", b.chargingTimeRemainingMin?.let { "$it min" })
                KeyValue("Ahorro de bateria", if (b.isBatterySaver) "activo" else "inactivo")
                Note("Signo de corriente segun el fuel gauge del OEM: positivo suele ser carga entrante, negativo consumo.")
            }
        }
        item {
            val sl = s.sleep
            val pct = sl.deepSleepPercentLastWindow
            Panel("Deep sleep", trailing = { StatusPill(if (running) "MONITOR ON" else "MONITOR OFF", alert = false, neutral = !running) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BigStat("Ultima ventana", pct?.let { "%.0f".format(it) } ?: "—", "%", color = sleepColor(pct))
                    BigStat("Desde boot", "%.0f".format(sl.deepSleepPercentSinceBoot), "%")
                    BigStat("Ventana", sl.lastWindowDurationMs?.let { formatDuration(it) } ?: "—")
                }
                Spacer(Modifier.height(8.dp))
                Text(sl.verdict, style = MaterialTheme.typography.bodyMedium, color = sleepColor(pct))
                Spacer(Modifier.height(8.dp))
                KeyValue("Uptime (CPU despierto)", formatDuration(sl.uptimeMs))
                KeyValue("Elapsed realtime", formatDuration(sl.elapsedRealtimeMs))
                KeyValue("Suspend total", formatDuration(sl.deepSleepTotalMs))
                KeyValue("Pantalla", if (sl.screenOn) "encendida" else "apagada")
                KeyValue("Doze (device idle)", if (sl.deviceIdleMode) "si" else "no")
                KeyValue("Light idle", if (sl.lightIdleMode) "si" else "no")
                KeyValue("Exenta de optimizacion", if (sl.ignoringBatteryOptimizations) "si" else "no")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (running) OutlinedButton(onClick = { vm.toggleMonitor(ctx) }) { Text("Detener monitor") }
                    else Button(onClick = { vm.toggleMonitor(ctx) }) { Text("Iniciar monitor") }
                }
                Note("Metodo: elapsedRealtime − uptimeMillis = tiempo en suspend. El servicio no retiene WakeLock para no contaminar la medida. Apaga la pantalla unos minutos y vuelve.")
                if (tracker.history.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    tracker.history.takeLast(5).reversed().forEach { w ->
                        KeyValue("Ventana ${formatDuration(w.durationMs)}", "%.0f %% sleep".format(w.sleepPercent), sleepColor(w.sleepPercent))
                    }
                }
            }
        }
        item {
            val t = s.thermal
            Panel("Termico", trailing = { StatusPill(t.status.uppercase(), alert = t.statusCode >= 3, neutral = t.statusCode == 0) }) {
                KeyValue("Estado del throttling", "${t.status} (${t.statusCode})")
                KeyValue("Headroom (10 s)", t.headroom?.let { "%.2f".format(it) })
                Note("Headroom: 1.0 = umbral de throttling severo. Valores < 0.8 son margen comodo.")
            }
        }
        item {
            val c = s.cpu
            Panel("CPU / SoC") {
                KeyValue("SoC", listOfNotNull(c.socManufacturer, c.socModel).joinToString(" ").takeIf { it.isNotBlank() })
                KeyValue("ABI", c.abi)
                KeyValue("Cores", c.cores.toString())
                if (c.currentFreqMhz.any { it > 0 }) {
                    c.currentFreqMhz.forEachIndexed { i, f ->
                        val max = c.maxFreqMhz.getOrNull(i) ?: 0
                        KeyValue("cpu$i", if (f > 0) "$f / $max MHz" else "offline / $max MHz", if (max > 0 && f > max * 0.85) Amber else MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    Note("Este kernel no expone cpufreq a apps de usuario (SELinux). Frecuencias no disponibles.")
                }
            }
        }
        item {
            val m = s.memory
            Panel("Memoria", trailing = { if (m.lowMemory) StatusPill("LOW MEM", alert = true) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BigStat("Libre", m.availableMb.toString(), "MB", color = if (m.lowMemory) Rose else Mint)
                    BigStat("Total", m.totalMb.toString(), "MB")
                    BigStat("Esta app", m.appPssMb.toString(), "MB PSS")
                }
                KeyValue("Umbral low-memory", "${m.thresholdMb} MB")
            }
        }
    }
}

private fun sleepColor(pct: Float?) = when {
    pct == null -> Cyan
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
