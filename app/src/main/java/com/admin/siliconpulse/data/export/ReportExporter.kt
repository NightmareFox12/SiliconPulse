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
