package com.admin.siliconpulse.ui.radios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.admin.siliconpulse.domain.model.RadioState
import com.admin.siliconpulse.domain.repository.RadioRepository
import com.admin.siliconpulse.ui.PermissionsBus
import com.admin.siliconpulse.ui.MonitoringController
import com.admin.siliconpulse.ui.components.Gauge
import com.admin.siliconpulse.ui.components.Hairline
import com.admin.siliconpulse.ui.components.HistoryChart
import com.admin.siliconpulse.ui.components.KeyValue
import com.admin.siliconpulse.ui.components.MetricTile
import com.admin.siliconpulse.ui.components.Note
import com.admin.siliconpulse.ui.components.Panel
import com.admin.siliconpulse.ui.components.SectionLabel
import com.admin.siliconpulse.ui.components.StatusPill
import com.admin.siliconpulse.ui.components.TwoCol
import com.admin.siliconpulse.ui.theme.Amber
import com.admin.siliconpulse.ui.theme.Cyan
import com.admin.siliconpulse.ui.theme.Mint
import com.admin.siliconpulse.ui.theme.Rose
import com.admin.siliconpulse.ui.theme.Sky
import com.admin.siliconpulse.ui.theme.TextMuted
import com.admin.siliconpulse.ui.theme.Violet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class RadioHistory(
    val wifiRssi: List<Float> = emptyList(),
    val cellDbm: List<Float> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class RadiosViewModel(repo: RadioRepository) : ViewModel() {
    private val _history = MutableStateFlow(RadioHistory())
    val history: StateFlow<RadioHistory> = _history

    val state: StateFlow<RadioState> = PermissionsBus.generation
        .flatMapLatest { MonitoringController.gate { repo.observe() } }
        .onEach { s ->
            _history.update { h ->
                RadioHistory(
                    wifiRssi = (h.wifiRssi + (s.wifi.rssiDbm?.toFloat() ?: h.wifiRssi.lastOrNull() ?: -100f)).takeLast(90),
                    cellDbm = (h.cellDbm + (s.cellular.signalDbm?.toFloat() ?: h.cellDbm.lastOrNull() ?: -120f)).takeLast(90),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RadioState())
}

@Composable
fun RadiosScreen(vm: RadiosViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val h by vm.history.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("Ruta de datos", s.activeTransport ?: "ninguna", color = Cyan, modifier = Modifier.weight(1f))
                MetricTile("Modo avion", if (s.airplaneMode) "ON" else "OFF", color = if (s.airplaneMode) Rose else Mint, modifier = Modifier.weight(1f))
            }
        }
        item {
            val c = s.cellular
            Panel("Red movil", subtitle = listOfNotNull(c.operatorName, c.networkTypeName).joinToString(" · ").ifBlank { "sin servicio" }, trailing = { StatusPill(c.generation ?: c.networkTypeName ?: "—", alert = false, neutral = c.generation == null) }) {
                if (!c.available) {
                    Text("Sin radio celular en este dispositivo.", color = TextMuted)
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Gauge(
                            fraction = dbmFraction(c.signalDbm, -120f, -60f),
                            value = c.signalDbm?.toString() ?: "—",
                            unit = "dBm",
                            label = "senal",
                            color = signalColor(c.signalDbm),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            SectionLabel("Historial · ${h.cellDbm.size * 2} s")
                            HistoryChart(series = listOf(h.cellDbm), colors = listOf(signalColor(c.signalDbm)), height = 110.dp, minY = -120f, maxY = -50f)
                        }
                    }
                    Hairline()
                    TwoCol(
                        left = { MetricTile("Nivel", c.signalLevel?.let { "$it/4" } ?: "—", color = Sky) },
                        right = { MetricTile("ASU", c.asuLevel?.toString() ?: "—", color = Violet) },
                    )
                    Spacer(Modifier.height(8.dp))
                    KeyValue("SIM", c.simOperator)
                    KeyValue("Estado 5G NR", c.nrState)
                    KeyValue("Datos moviles", c.dataState)
                    KeyValue("Roaming", if (c.roaming) "si" else "no")
                    KeyValue("Celda registrada", c.cellIdentity)
                    if (!c.permissionGranted) Note("Sin READ_PHONE_STATE el tipo de red y el ServiceState no estan disponibles.")
                }
            }
        }
        item {
            val w = s.wifi
            Panel("Wi-Fi", subtitle = listOfNotNull(w.ssid, w.standard).joinToString(" · ").ifBlank { if (w.connected) "conectado" else "sin red" }, accent = Sky, trailing = { StatusPill(if (w.connected) "CONECTADO" else if (w.enabled) "SIN RED" else "APAGADO", alert = false, neutral = !w.connected) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Gauge(
                        fraction = dbmFraction(w.rssiDbm, -95f, -40f),
                        value = w.rssiDbm?.toString() ?: "—",
                        unit = "dBm",
                        label = "rssi",
                        color = signalColor(w.rssiDbm),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Historial · ${h.wifiRssi.size * 2} s")
                        HistoryChart(series = listOf(h.wifiRssi), colors = listOf(signalColor(w.rssiDbm)), height = 110.dp, minY = -95f, maxY = -30f)
                    }
                }
                Hairline()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Link", w.linkSpeedMbps?.toString() ?: "—", "Mbps", Sky, Modifier.weight(1f))
                    MetricTile("Banda", w.band ?: "—", color = Violet, modifier = Modifier.weight(1f))
                    MetricTile("Canal", w.frequencyMhz?.let { "$it" } ?: "—", "MHz", Cyan, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                KeyValue("BSSID", w.bssid)
                KeyValue("TX / RX", if (w.txLinkSpeedMbps != null || w.rxLinkSpeedMbps != null) "${w.txLinkSpeedMbps ?: "?"} / ${w.rxLinkSpeedMbps ?: "?"} Mbps" else null)
                KeyValue("IPv4", w.ipAddress)
                KeyValue("Ancho estimado ↓/↑", if (w.downstreamKbps != null) "${w.downstreamKbps / 1000} / ${(w.upstreamKbps ?: 0) / 1000} Mbps" else null)
                KeyValue("Medida (metered)", w.metered?.let { if (it) "si" else "no" })
                if (w.connected && w.ssid == null) Note("El SSID requiere permiso de ubicacion y que la ubicacion del sistema este activa.")
            }
        }
        item {
            val b = s.bluetooth
            Panel("Bluetooth", subtitle = b.adapterName ?: "—", accent = Violet, trailing = { StatusPill(if (b.enabled) "ON" else "OFF", alert = false, neutral = !b.enabled) }) {
                if (!b.supported) {
                    Text("Sin adaptador Bluetooth.", color = TextMuted)
                } else if (!b.permissionGranted) {
                    Note("Concede BLUETOOTH_CONNECT para listar los dispositivos vinculados.")
                } else {
                    TwoCol(
                        left = { MetricTile("Vinculados", b.devices.size.toString(), color = Violet) },
                        right = { MetricTile("Conectados", b.devices.count { it.connected }.toString(), color = if (b.devices.any { it.connected }) Mint else TextMuted) },
                    )
                    Spacer(Modifier.height(8.dp))
                    KeyValue("Perfiles activos", b.connectedProfiles.takeIf { it.isNotEmpty() }?.joinToString())
                    if (b.devices.isNotEmpty()) Hairline()
                    b.devices.sortedByDescending { it.connected }.forEach { d ->
                        KeyValue(
                            "${d.name} · ${d.type}",
                            if (d.connected) "CONECTADO" else d.address,
                            if (d.connected) Mint else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun dbmFraction(dbm: Int?, min: Float, max: Float): Float =
    if (dbm == null) 0f else ((dbm - min) / (max - min)).coerceIn(0f, 1f)

private fun signalColor(dbm: Int?) = when {
    dbm == null -> Cyan
    dbm >= -70 -> Mint
    dbm >= -90 -> Amber
    else -> Rose
}
