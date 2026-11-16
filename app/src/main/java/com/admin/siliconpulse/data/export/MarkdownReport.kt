package com.admin.siliconpulse.data.export

import com.admin.siliconpulse.domain.model.DiagnosticReport
import com.admin.siliconpulse.domain.model.Verdict

/**
 * Informe en Markdown con tres niveles de lectura:
 *  1. Resumen ejecutivo: estado de salud y alertas en cinco lineas.
 *  2. Tablas de estado: componentes, red, energia y sensores, un vistazo por fila.
 *  3. Detalle tecnico: notas y metadatos del equipo.
 * Se abre en cualquier editor o navegador y lo entiende tanto un tecnico como una IA.
 */
object MarkdownReport {

    data class Alert(val level: Verdict, val text: String)

    fun alerts(r: DiagnosticReport): List<Alert> = buildList {
        if (r.privacy.camera.inUse) add(Alert(Verdict.ALERT, "Camara en uso en el momento de la captura (${r.privacy.camera.unavailableIds.size} ocupada/s)"))
        if (r.privacy.microphone.inUse) add(Alert(Verdict.ALERT, "Microfono en uso: ${r.privacy.microphone.activeRecordings} grabacion/es (${r.privacy.microphone.sources.joinToString()})"))
        val b = r.power.battery
        b.temperatureC?.let { if (it >= 42f) add(Alert(Verdict.WARN, "Bateria caliente: %.1f C".format(it))) }
        if (b.health !in setOf("good", "unknown")) add(Alert(Verdict.WARN, "Salud de bateria: ${b.health}"))
        b.currentNowMa?.let { if (it < -1500f) add(Alert(Verdict.WARN, "Consumo alto: %.0f mA".format(it))) }
        if (r.power.thermal.statusCode >= 3) add(Alert(Verdict.ALERT, "Throttling termico: ${r.power.thermal.status}"))
        r.power.sleep.deepSleepPercentLastWindow?.let {
            if (it < 40f) add(Alert(Verdict.ALERT, "Sin deep sleep: solo %.0f %% con pantalla apagada (wakelock probable)".format(it)))
            else if (it < 80f) add(Alert(Verdict.WARN, "Deep sleep parcial: %.0f %% con pantalla apagada".format(it)))
        }
        if (r.power.memory.lowMemory) add(Alert(Verdict.WARN, "Memoria baja: ${r.power.memory.availableMb} MB libres"))
        r.radios.cellular.signalDbm?.let { if (it <= -105) add(Alert(Verdict.WARN, "Senal movil muy debil: $it dBm")) }
    }

    fun overall(alerts: List<Alert>): Verdict = when {
        alerts.any { it.level == Verdict.ALERT } -> Verdict.ALERT
        alerts.any { it.level == Verdict.WARN } -> Verdict.WARN
        else -> Verdict.OK
    }

    fun render(r: DiagnosticReport): String = buildString {
        val al = alerts(r)
        val ov = overall(al)
        val d = r.device
        appendLine("# Informe SiliconPulse")
        appendLine()
        appendLine("## 1. Resumen ejecutivo")
        appendLine()
        appendLine("| | |")
        appendLine("|---|---|")
        appendLine("| **Estado general** | ${badge(ov)} |")
        appendLine("| **Fecha** | ${r.generatedAtIso} |")
        appendLine("| **Equipo** | ${d.manufacturer} ${d.model} (${d.device}) |")
        appendLine("| **Android** | ${d.androidVersion} (API ${d.sdkInt}), parche ${d.securityPatch} |")
        appendLine("| **SoC** | ${listOfNotNull(r.power.cpu.socManufacturer, r.power.cpu.socModel).joinToString(" ").ifBlank { "n/d" }} · ${r.power.cpu.cores} cores · ${r.power.cpu.abi ?: ""} |")
        appendLine("| **Alertas** | ${if (al.isEmpty()) "ninguna" else "${al.count { it.level == Verdict.ALERT }} criticas, ${al.count { it.level == Verdict.WARN }} avisos"} |")
        appendLine()
        if (al.isNotEmpty()) {
            appendLine("### Alertas")
            appendLine()
            al.forEach { appendLine("- ${badge(it.level)} ${it.text}") }
            appendLine()
        }

        appendLine("## 2. Estado por componente")
        appendLine()
        appendLine("### Privacidad")
        appendLine()
        appendLine("| Componente | Estado | Detalle |")
        appendLine("|---|---|---|")
        appendLine("| Camara | ${if (r.privacy.camera.inUse) "EN USO" else "libre"} | ${r.privacy.camera.totalCameras} camaras, ocupadas: ${r.privacy.camera.unavailableIds.joinToString().ifBlank { "ninguna" }} |")
        appendLine("| Microfono | ${if (r.privacy.microphone.inUse) "EN USO" else "silencio"} | ${r.privacy.microphone.activeRecordings} grabaciones ${r.privacy.microphone.sources.joinToString().let { if (it.isBlank()) "" else "($it)" }} |")
        val loc = r.privacy.location
        appendLine("| Ubicacion | ${if (loc.locationEnabled) "encendida" else "apagada"} | GPS ${if (loc.gpsProviderEnabled) "on" else "off"}, red ${if (loc.networkProviderEnabled) "on" else "off"}, satelites ${loc.satellitesUsedInFix}/${loc.satellitesVisible}${loc.accuracyMeters?.let { ", precision %.0f m".format(it) } ?: ""} |")
        appendLine()

        appendLine("### Red")
        appendLine()
        appendLine("| Radio | Estado | Detalle |")
        appendLine("|---|---|---|")
        appendLine("| Transporte activo | ${r.radios.activeTransport ?: "ninguno"} | ${if (r.radios.airplaneMode) "modo avion" else ""} |")
        val c = r.radios.cellular
        appendLine("| Movil | ${if (!c.available) "sin radio" else c.generation ?: c.networkTypeName ?: "?"} | ${listOfNotNull(c.operatorName, c.signalDbm?.let { "$it dBm" }, c.signalLevel?.let { "nivel $it/4" }, c.nrState?.let { "NR $it" }, "datos ${c.dataState}").joinToString(", ")} |")
        val w = r.radios.wifi
        appendLine("| Wi-Fi | ${if (w.connected) "conectado" else if (w.enabled) "sin red" else "apagado"} | ${listOfNotNull(w.ssid, w.standard, w.band, w.rssiDbm?.let { "$it dBm" }, w.linkSpeedMbps?.let { "link $it Mbps" }, w.ipAddress).joinToString(", ")} |")
        val bt = r.radios.bluetooth
        appendLine("| Bluetooth | ${if (bt.enabled) "encendido" else "apagado"} | ${bt.devices.size} vinculados, ${bt.devices.count { it.connected }} conectados${bt.connectedProfiles.takeIf { it.isNotEmpty() }?.let { " (${it.joinToString()})" } ?: ""} |")
        appendLine()

        appendLine("### Energia")
        appendLine()
        val b = r.power.battery
        appendLine("| Metrica | Valor |")
        appendLine("|---|---|")
        appendLine("| Carga | ${b.levelPercent} % (${b.status}, ${b.plugged}) |")
        appendLine("| Corriente | ${b.currentNowMa?.let { "%.0f mA".format(it) } ?: "n/d"} (prom. ${b.currentAverageMa?.let { "%.0f mA".format(it) } ?: "n/d"}) |")
        appendLine("| Voltaje / Temp | ${b.voltageMv?.let { "%.2f V".format(it / 1000f) } ?: "n/d"} / ${b.temperatureC?.let { "%.1f C".format(it) } ?: "n/d"} |")
        appendLine("| Salud / Ciclos | ${b.health} / ${b.cycleCount ?: "n/d"} |")
        appendLine("| Termico | ${r.power.thermal.status}${r.power.thermal.headroom?.let { " (headroom %.2f)".format(it) } ?: ""} |")
        val sl = r.power.sleep
        appendLine("| Deep sleep | ${sl.deepSleepPercentLastWindow?.let { "%.0f %% en ultima ventana".format(it) } ?: "sin ventana medida"}; desde boot %.0f %% |".format(sl.deepSleepPercentSinceBoot))
        appendLine("| Doze / Ahorro | ${if (sl.deviceIdleMode) "si" else "no"} / ${if (sl.powerSaveMode) "si" else "no"} |")
        appendLine("| Memoria | ${r.power.memory.availableMb} MB libres de ${r.power.memory.totalMb} MB${if (r.power.memory.lowMemory) " (LOW)" else ""} |")
        if (r.power.cpu.currentFreqMhz.any { it > 0 }) {
            appendLine("| CPU MHz | ${r.power.cpu.currentFreqMhz.joinToString(" / ")} |")
        }
        appendLine()

        appendLine("### Sensores")
        appendLine()
        appendLine("| Canal | Disponible | X | Y | Z | Hz |")
        appendLine("|---|---|---|---|---|---|")
        r.sensorSnapshots.forEach { s ->
            appendLine("| ${s.channel} (${s.unit}) | ${if (s.available) "si" else "no"} | ${fmt(s.lastX)} | ${fmt(s.lastY)} | ${fmt(s.lastZ)} | ${"%.0f".format(s.sampleRateHz)} |")
        }
        appendLine()

        appendLine("## 3. Detalle tecnico")
        appendLine()
        if (r.notes.isNotEmpty()) {
            appendLine("### Notas de cobertura")
            appendLine()
            r.notes.forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("### Sensores reportados por el HAL (${r.sensorsAvailable.size})")
        appendLine()
        appendLine("| Nombre | Tipo | Vendor | Rango | Consumo mA | Modo |")
        appendLine("|---|---|---|---|---|---|")
        r.sensorsAvailable.forEach { s ->
            appendLine("| ${s.name} | ${s.typeName.removePrefix("android.sensor.")} | ${s.vendor} | ${"%.3g".format(s.maxRange)} | ${"%.2f".format(s.powerMa)} | ${s.reportingMode}${if (s.isWakeUp) ", wake-up" else ""} |")
        }
        appendLine()
        appendLine("### Equipo")
        appendLine()
        appendLine("- Fingerprint: `${d.fingerprint}`")
        appendLine("- Kernel: `${d.kernelVersion}`")
        appendLine("- Build: ${d.buildId}")
        appendLine("- App: SiliconPulse ${r.appVersion}")
    }

    /** Prompt para pegar en una IA: contexto + instrucciones + informe. */
    fun aiPrompt(r: DiagnosticReport): String = buildString {
        appendLine("Actua como un tecnico de Android experto en privacidad, bateria y hardware.")
        appendLine("Abajo tienes un informe de diagnostico generado por la app SiliconPulse en mi telefono.")
        appendLine("Explicame en lenguaje claro, sin jerga innecesaria:")
        appendLine("1. Si hay algo preocupante en privacidad (camara, microfono, ubicacion).")
        appendLine("2. Si la bateria, la temperatura o el deep sleep indican que algo esta mal.")
        appendLine("3. Que deberia hacer, paso a paso, para corregir cada problema.")
        appendLine("Si todo esta bien, dimelo claramente. Responde en espanol.")
        appendLine()
        appendLine("---")
        appendLine()
        append(render(r))
    }

    private fun badge(v: Verdict) = when (v) { Verdict.OK -> "🟢 OK"; Verdict.WARN -> "🟡 Revisar"; Verdict.ALERT -> "🔴 Alerta" }
    private fun fmt(v: Float) = if (v == 0f) "0" else "%.3g".format(v)
}
