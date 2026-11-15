package com.admin.siliconpulse.ui.export

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.admin.siliconpulse.data.export.ReportExporter
import com.admin.siliconpulse.domain.model.DiagnosticReport
import com.admin.siliconpulse.domain.model.SamplingRate
import com.admin.siliconpulse.domain.model.SensorChannel
import com.admin.siliconpulse.domain.model.SensorChannelSnapshot
import com.admin.siliconpulse.domain.repository.PowerRepository
import com.admin.siliconpulse.domain.repository.PrivacyRepository
import com.admin.siliconpulse.domain.repository.RadioRepository
import com.admin.siliconpulse.domain.repository.SensorRepository
import com.admin.siliconpulse.ui.components.KeyValue
import com.admin.siliconpulse.ui.components.Note
import com.admin.siliconpulse.ui.components.Panel
import com.admin.siliconpulse.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ExportUiState(
    val building: Boolean = false,
    val report: DiagnosticReport? = null,
    val json: String? = null,
    val message: String? = null,
)

class ExportViewModel(
    private val sensors: SensorRepository,
    private val privacy: PrivacyRepository,
    private val radios: RadioRepository,
    private val power: PowerRepository,
    private val exporter: ReportExporter,
) : ViewModel() {

    private val _ui = MutableStateFlow(ExportUiState())
    val ui: StateFlow<ExportUiState> = _ui

    fun buildSnapshot() {
        if (_ui.value.building) return
        _ui.update { it.copy(building = true, message = null) }
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    // Abrimos cada canal ~1 s en paralelo para capturar un valor real y su tasa.
                    val snapshotsDeferred = SensorChannel.entries.map { ch ->
                        async(Dispatchers.Default) { sampleChannel(ch) }
                    }
                    val privacyDeferred = async { withTimeoutOrNull(3000) { privacy.observe().take(3).toList().last() } }
                    val radiosDeferred = async { withTimeoutOrNull(3000) { radios.observe().first() } }
                    val powerDeferred = async { withTimeoutOrNull(3000) { power.observe().first() } }

                    val report = exporter.build(
                        sensors = sensors.listSensors(),
                        snapshots = snapshotsDeferred.awaitAll(),
                        privacy = privacyDeferred.await() ?: com.admin.siliconpulse.domain.model.PrivacyState(),
                        radios = radiosDeferred.await() ?: com.admin.siliconpulse.domain.model.RadioState(),
                        power = powerDeferred.await() ?: com.admin.siliconpulse.domain.model.PowerState(),
                    )
                    val json = withContext(Dispatchers.Default) { exporter.toJson(report) }
                    _ui.update { it.copy(building = false, report = report, json = json, message = "Snapshot listo: ${json.length / 1024} KB") }
                }
            }.onFailure { e ->
                _ui.update { it.copy(building = false, message = "Error: ${e.message}") }
            }
        }
    }

    private suspend fun sampleChannel(ch: SensorChannel): SensorChannelSnapshot {
        if (!sensors.isAvailable(ch)) {
            return SensorChannelSnapshot(ch.name, ch.unit, available = false, 0f, 0f, 0f, 0f, 0)
        }
        val samples = withTimeoutOrNull(1500) { sensors.samples(ch, SamplingRate.UI).take(20).toList() } ?: emptyList()
        if (samples.isEmpty()) return SensorChannelSnapshot(ch.name, ch.unit, available = true, 0f, 0f, 0f, 0f, 0)
        val last = samples.last()
        val span = (last.timestampNanos - samples.first().timestampNanos).coerceAtLeast(1)
        val hz = if (samples.size > 1) (samples.size - 1) * 1e9f / span else 0f
        return SensorChannelSnapshot(ch.name, ch.unit, true, last.x, last.y, last.z, hz, last.accuracy)
    }

    fun suggestedName() = exporter.suggestedFileName()

    fun writeTo(uri: Uri, resolver: android.content.ContentResolver) {
        val json = _ui.value.json ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("No se pudo abrir el destino")
            }.onSuccess { _ui.update { it.copy(message = "Guardado en ${uri.lastPathSegment}") } }
                .onFailure { e -> _ui.update { it.copy(message = "Error al guardar: ${e.message}") } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(vm: ExportViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.writeTo(uri, ctx.contentResolver)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel("Reporte de diagnostico") {
                Text(
                    "Empaqueta en un JSON: dispositivo, lista de sensores del HAL, una muestra real de cada canal, " +
                        "estado de microfono/camara/ubicacion, radios y energia (incluida la ventana de deep sleep).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = vm::buildSnapshot, enabled = !ui.building) {
                        if (ui.building) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp) else Text("Capturar snapshot")
                    }
                    OutlinedButton(onClick = { saveLauncher.launch(vm.suggestedName()) }, enabled = ui.json != null) { Text("Guardar .json") }
                    OutlinedButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, vm.suggestedName())
                                putExtra(Intent.EXTRA_TEXT, ui.json)
                            }
                            ctx.startActivity(Intent.createChooser(send, "Compartir reporte"))
                        },
                        enabled = ui.json != null,
                    ) { Text("Compartir") }
                }
                ui.message?.let { Note(it) }
            }
        }
        ui.report?.let { r ->
            item {
                Panel("Resumen del snapshot") {
                    KeyValue("Generado", r.generatedAtIso)
                    KeyValue("Dispositivo", "${r.device.manufacturer} ${r.device.model}")
                    KeyValue("Android", "${r.device.androidVersion} (API ${r.device.sdkInt}) · parche ${r.device.securityPatch}")
                    KeyValue("Kernel", r.device.kernelVersion)
                    KeyValue("Sensores HAL", r.sensorsAvailable.size.toString())
                    KeyValue("Canales con datos", r.sensorSnapshots.count { it.available && it.sampleRateHz > 0 }.toString())
                    KeyValue("Mic / Cam en uso", "${r.privacy.microphone.inUse} / ${r.privacy.camera.inUse}")
                    KeyValue("Transporte", r.radios.activeTransport)
                    KeyValue("Bateria", "${r.power.battery.levelPercent}% · ${r.power.battery.currentNowMa?.let { "%.0f mA".format(it) } ?: "—"}")
                    KeyValue("Deep sleep ultima ventana", r.power.sleep.deepSleepPercentLastWindow?.let { "%.0f %%".format(it) })
                    if (r.notes.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Column { r.notes.forEach { Note("• $it") } }
                    }
                }
            }
            item {
                Panel("Vista previa JSON") {
                    Text(
                        ui.json?.take(4000) ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                    if ((ui.json?.length ?: 0) > 4000) Note("… truncado en la vista previa; el archivo completo se guarda integro.")
                }
            }
        }
    }
}
