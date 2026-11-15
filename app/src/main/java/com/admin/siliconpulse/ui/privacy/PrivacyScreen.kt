package com.admin.siliconpulse.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.admin.siliconpulse.domain.model.PrivacyState
import com.admin.siliconpulse.domain.repository.PrivacyRepository
import com.admin.siliconpulse.ui.PermissionsBus
import com.admin.siliconpulse.ui.components.KeyValue
import com.admin.siliconpulse.ui.components.Note
import com.admin.siliconpulse.ui.components.Panel
import com.admin.siliconpulse.ui.components.StatusPill
import com.admin.siliconpulse.ui.theme.Mint
import com.admin.siliconpulse.ui.theme.Rose
import com.admin.siliconpulse.ui.theme.TextMuted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModel(repo: PrivacyRepository) : ViewModel() {
    val state: StateFlow<PrivacyState> = PermissionsBus.generation
        .flatMapLatest { repo.observe() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyState())
}

@Composable
fun PrivacyScreen(vm: PrivacyViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel("Microfono", trailing = { StatusPill(if (s.microphone.inUse) "EN USO" else "LIBRE", alert = s.microphone.inUse) }) {
                KeyValue("Grabaciones activas (sistema)", s.microphone.activeRecordings.toString(), if (s.microphone.activeRecordings > 0) Rose else Mint)
                KeyValue("Fuentes", s.microphone.sources.takeIf { it.isNotEmpty() }?.joinToString())
                KeyValue("Toggle HW de privacidad", if (s.microphone.hardwareMuted) "SILENCIADO" else "activo")
                KeyValue("Paquetes (AppOps)", s.microphone.appOpsPackages.takeIf { it.isNotEmpty() }?.joinToString())
                Note("AudioManager expone todas las grabaciones activas del dispositivo; los paquetes de terceros van anonimizados por el sistema.")
            }
        }
        item {
            Panel("Camara", trailing = { StatusPill(if (s.camera.inUse) "EN USO" else "LIBRE", alert = s.camera.inUse) }) {
                KeyValue("Camaras fisicas", s.camera.totalCameras.toString())
                KeyValue("IDs ocupadas", s.camera.unavailableIds.takeIf { it.isNotEmpty() }?.joinToString(), Rose)
                KeyValue("Toggle HW de privacidad", if (s.camera.hardwareMuted) "BLOQUEADA" else "activa")
                KeyValue("Paquetes (AppOps)", s.camera.appOpsPackages.takeIf { it.isNotEmpty() }?.joinToString())
                Note("CameraManager marca una camara como no disponible cuando cualquier app la abre.")
            }
        }
        item {
            val loc = s.location
            Panel("Ubicacion", trailing = { StatusPill(if (loc.locationEnabled) "ON" else "OFF", alert = false, neutral = !loc.locationEnabled) }) {
                if (!loc.permissionGranted) {
                    Text("Sin permiso de ubicacion. Concedelo desde el aviso superior para ver GNSS.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                }
                KeyValue("GPS provider", if (loc.gpsProviderEnabled) "habilitado" else "deshabilitado")
                KeyValue("Network provider", if (loc.networkProviderEnabled) "habilitado" else "deshabilitado")
                KeyValue("Ultima posicion", loc.latitude?.let { "%.5f, %.5f".format(it, loc.longitude) })
                KeyValue("Precision", loc.accuracyMeters?.let { "%.1f m".format(it) })
                KeyValue("Altitud", loc.altitudeMeters?.let { "%.1f m".format(it) })
                KeyValue("Velocidad", loc.speedMps?.let { "%.1f m/s".format(it) })
                KeyValue("Proveedor del fix", loc.provider)
                KeyValue("Edad del fix", loc.fixAgeSeconds?.let { "$it s" })
                KeyValue("Satelites visibles / en fix", "${loc.satellitesVisible} / ${loc.satellitesUsedInFix}")
            }
        }
        item {
            Panel("Cobertura del monitor") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("AudioManager", alert = false)
                    StatusPill("CameraManager", alert = false)
                    StatusPill("AppOps 3ros", alert = !s.appOpsWatchAvailable, neutral = !s.appOpsWatchAvailable)
                }
                Note(
                    if (s.appOpsWatchAvailable) "AppOps permitio vigilar ops de terceros."
                    else "AppOps solo reporta ops de esta app sin WATCH_APPOPS (permiso de sistema). Para auditar terceros por paquete: adb shell cmd appops ... o un build userdebug."
                )
            }
        }
    }
}
