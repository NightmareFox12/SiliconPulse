package com.admin.siliconpulse.data.export

import android.content.Context
import android.os.Build
import com.admin.siliconpulse.domain.model.DeviceInfo
import com.admin.siliconpulse.domain.model.DiagnosticReport
import com.admin.siliconpulse.domain.model.PowerState
import com.admin.siliconpulse.domain.model.PrivacyState
import com.admin.siliconpulse.domain.model.RadioState
import com.admin.siliconpulse.domain.model.SensorChannelSnapshot
import com.admin.siliconpulse.domain.model.SensorDescriptor
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ReportExporter(private val context: Context) {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun build(
        sensors: List<SensorDescriptor>,
        snapshots: List<SensorChannelSnapshot>,
        privacy: PrivacyState,
        radios: RadioState,
        power: PowerState,
    ): DiagnosticReport {
        val now = System.currentTimeMillis()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date(now))
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        val notes = buildList {
            if (!privacy.appOpsWatchAvailable) add("AppOps watch de terceros no disponible (requiere WATCH_APPOPS). Microfono/camara se infieren via AudioManager y CameraManager.")
            if (!privacy.location.permissionGranted) add("Sin permiso de ubicacion: SSID, celdas y GNSS limitados.")
            if (!radios.bluetooth.permissionGranted) add("Sin BLUETOOTH_CONNECT: lista de dispositivos no disponible.")
            if (power.sleep.deepSleepPercentLastWindow == null) add("No hay ventana de pantalla apagada medida; activa el monitor de deep sleep.")
            if (power.cpu.currentFreqMhz.all { it == 0 }) add("El kernel no expone cpufreq en sysfs para apps de usuario.")
        }
        return DiagnosticReport(
            generatedAtEpochMs = now,
            generatedAtIso = iso,
            appVersion = version,
            device = deviceInfo(),
            sensorsAvailable = sensors,
            sensorSnapshots = snapshots,
            privacy = privacy,
            radios = radios,
            power = power,
            notes = notes,
        )
    }

    fun toJson(report: DiagnosticReport): String = json.encodeToString(DiagnosticReport.serializer(), report)

    /** Resumen en espanol llano, pensado para pegar en WhatsApp o enviar a un tecnico. */
    fun toPlainText(r: DiagnosticReport): String = buildString {
        appendLine("SiliconPulse - Informe del telefono")
        appendLine("Fecha: ${r.generatedAtIso}")
        appendLine("Equipo: ${r.device.manufacturer} ${r.device.model} - Android ${r.device.androidVersion} (parche ${r.device.securityPatch})")
        appendLine()
        appendLine("PRIVACIDAD")
        appendLine("- Camara: " + if (r.privacy.camera.inUse) "EN USO (${r.privacy.camera.unavailableIds.size} ocupada/s)" else "libre")
        appendLine("- Microfono: " + if (r.privacy.microphone.inUse) "EN USO (${r.privacy.microphone.activeRecordings} grabacion/es: ${r.privacy.microphone.sources.joinToString()})" else "en silencio")
        val loc = r.privacy.location
        appendLine("- Ubicacion: " + (if (loc.locationEnabled) "encendida" else "apagada") + (loc.accuracyMeters?.let { " - precision %.0f m, %d satelites en fix".format(it, loc.satellitesUsedInFix) } ?: ""))
        appendLine()
        appendLine("RED")
        appendLine("- Datos por: " + (r.radios.activeTransport ?: "ninguna") + if (r.radios.airplaneMode) " (modo avion)" else "")
        val c = r.radios.cellular
        if (c.available) appendLine("- Movil: " + listOfNotNull(c.operatorName, c.generation ?: c.networkTypeName, c.signalDbm?.let { "$it dBm" }).joinToString(" "))
        val w = r.radios.wifi
        if (w.connected) appendLine("- Wi-Fi: " + listOfNotNull(w.ssid ?: "(SSID oculto)", w.band, w.rssiDbm?.let { "$it dBm" }, w.linkSpeedMbps?.let { "$it Mbps" }).joinToString(" "))
        appendLine("- Bluetooth: " + if (r.radios.bluetooth.enabled) "encendido, ${r.radios.bluetooth.devices.count { it.connected }} conectado/s" else "apagado")
        appendLine()
        appendLine("ENERGIA")
        val b = r.power.battery
        appendLine("- Bateria: ${b.levelPercent}% ${b.status}" + (b.temperatureC?.let { ", %.1f C".format(it) } ?: "") + (b.currentNowMa?.let { ", %.0f mA".format(it) } ?: "") + (b.health.takeIf { it != "unknown" }?.let { ", salud $it" } ?: ""))
        appendLine("- Termico: ${r.power.thermal.status}" + (r.power.thermal.headroom?.let { " (headroom %.2f)".format(it) } ?: ""))
        appendLine("- Deep sleep: " + (r.power.sleep.deepSleepPercentLastWindow?.let { "%.0f%% en la ultima ventana - ".format(it) } ?: "") + r.power.sleep.verdict)
        appendLine("- Memoria libre: ${r.power.memory.availableMb} de ${r.power.memory.totalMb} MB")
        appendLine()
        appendLine("SENSORES: ${r.sensorsAvailable.size} reportados por el fabricante, ${r.sensorSnapshots.count { it.available && it.sampleRateHz > 0 }} canales con datos")
        if (r.notes.isNotEmpty()) { appendLine(); appendLine("NOTAS"); r.notes.forEach { appendLine("- $it") } }
    }

    fun suggestedFileName(): String =
        "siliconpulse_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json"

    private fun deviceInfo() = DeviceInfo(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        device = Build.DEVICE,
        board = Build.BOARD,
        hardware = Build.HARDWARE,
        androidVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        securityPatch = Build.VERSION.SECURITY_PATCH,
        buildId = Build.DISPLAY,
        fingerprint = Build.FINGERPRINT,
        kernelVersion = System.getProperty("os.version") ?: "?",
    )
}
