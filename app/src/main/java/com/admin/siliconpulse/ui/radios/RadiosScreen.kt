package com.admin.siliconpulse.ui.radios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.admin.siliconpulse.domain.model.RadioState
import com.admin.siliconpulse.domain.repository.RadioRepository
import com.admin.siliconpulse.ui.PermissionsBus
import com.admin.siliconpulse.ui.components.BigStat
import com.admin.siliconpulse.ui.components.KeyValue
import com.admin.siliconpulse.ui.components.Note
import com.admin.siliconpulse.ui.components.Panel
import com.admin.siliconpulse.ui.components.StatusPill
import com.admin.siliconpulse.ui.theme.Amber
import com.admin.siliconpulse.ui.theme.Cyan
import com.admin.siliconpulse.ui.theme.Mint
import com.admin.siliconpulse.ui.theme.Rose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class RadiosViewModel(repo: RadioRepository) : ViewModel() {
    val state: StateFlow<RadioState> = PermissionsBus.generation
        .flatMapLatest { repo.observe() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RadioState())
}

@Composable
fun RadiosScreen(vm: RadiosViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel("Transporte activo", trailing = { if (s.airplaneMode) StatusPill("MODO AVION", alert = true) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BigStat("Ruta de datos", s.activeTransport ?: "ninguna", color = Cyan)
                }
            }
        }
        item {
            val c = s.cellular
            Panel("Red movil", trailing = { StatusPill(c.generation ?: c.networkTypeName ?: "—", alert = false, neutral = c.generation == null) }) {
                if (!c.available) {
                    Text("Sin radio celular en este dispositivo.")
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BigStat("Senal", c.signalDbm?.toString() ?: "—", "dBm", color = signalColor(c.signalDbm))
                        BigStat("Nivel", c.signalLevel?.let { "$it/4" } ?: "—")
                        BigStat("ASU", c.asuLevel?.toString() ?: "—")
                    }
                    KeyValue("Operador", c.operatorName)
                    KeyValue("SIM", c.simOperator)
                    KeyValue("Tecnologia", c.networkTypeName)
                    KeyValue("Estado 5G NR", c.nrState)
                    KeyValue("Datos", c.dataState)
                    KeyValue("Roaming", if (c.roaming) "si" else "no")
                    KeyValue("Celda registrada", c.cellIdentity)
                    if (!c.permissionGranted) Note("Sin READ_PHONE_STATE el tipo de red y el ServiceState no estan disponibles.")
                }
            }
        }
        item {
            val w = s.wifi
            Panel("Wi-Fi", trailing = { StatusPill(if (w.connected) "CONECTADO" else if (w.enabled) "SIN RED" else "APAGADO", alert = false, neutral = !w.connected) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BigStat("RSSI", w.rssiDbm?.toString() ?: "—", "dBm", color = signalColor(w.rssiDbm))
                    BigStat("Link", w.linkSpeedMbps?.toString() ?: "—", "Mbps")
                    BigStat("Banda", w.band ?: "—")
                }
                KeyValue("SSID", w.ssid)
                KeyValue("BSSID", w.bssid)
                KeyValue("Frecuencia", w.frequencyMhz?.let { "$it MHz" })
                KeyValue("Estandar", w.standard)
                KeyValue("TX / RX", if (w.txLinkSpeedMbps != null || w.rxLinkSpeedMbps != null) "${w.txLinkSpeedMbps ?: "?"} / ${w.rxLinkSpeedMbps ?: "?"} Mbps" else null)
                KeyValue("IPv4", w.ipAddress)
                KeyValue("Ancho estimado ↓/↑", if (w.downstreamKbps != null) "${w.downstreamKbps / 1000} / ${(w.upstreamKbps ?: 0) / 1000} Mbps" else null)
                KeyValue("Medida (metered)", w.metered?.let { if (it) "si" else "no" })
                if (w.connected && w.ssid == null) Note("El SSID requiere permiso de ubicacion y que la ubicacion del sistema este activa.")
            }
        }
        item {
            val b = s.bluetooth
            Panel("Bluetooth", trailing = { StatusPill(if (b.enabled) "ON" else "OFF", alert = false, neutral = !b.enabled) }) {
                if (!b.supported) {
                    Text("Sin adaptador Bluetooth.")
                } else if (!b.permissionGranted) {
                    Note("Concede BLUETOOTH_CONNECT para listar los dispositivos vinculados.")
                } else {
                    KeyValue("Adaptador", b.adapterName)
                    KeyValue("Perfiles conectados", b.connectedProfiles.takeIf { it.isNotEmpty() }?.joinToString())
                    KeyValue("Vinculados", b.devices.size.toString())
                    b.devices.sortedByDescending { it.connected }.forEach { d ->
                        KeyValue(
                            "${d.name} · ${d.type}",
                            if (d.connected) "CONECTADO" else d.address,
                            if (d.connected) Mint else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun signalColor(dbm: Int?) = when {
    dbm == null -> Cyan
    dbm >= -70 -> Mint
    dbm >= -90 -> Amber
    else -> Rose
}
