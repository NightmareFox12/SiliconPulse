package com.admin.siliconpulse.ui.simple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.admin.siliconpulse.data.audit.AppAuditRepository
import com.admin.siliconpulse.domain.model.AuditedApp
import com.admin.siliconpulse.domain.model.PowerState
import com.admin.siliconpulse.domain.model.PrivacyState
import com.admin.siliconpulse.domain.model.SimpleFinding
import com.admin.siliconpulse.domain.model.SimpleReport
import com.admin.siliconpulse.domain.model.Verdict
import com.admin.siliconpulse.domain.repository.PowerRepository
import com.admin.siliconpulse.domain.repository.PrivacyRepository
import com.admin.siliconpulse.ui.MonitoringController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class SimpleUiState(
    val analyzing: Boolean = false,
    val progress: String? = null,
    val report: SimpleReport? = null,
    val error: String? = null,
)

/**
 * Traduce la telemetria a frases que cualquiera entiende y cruza el estado de
 * camara/microfono con el inventario de apps para senalar probables responsables.
 */
class SimpleViewModel(
    private val privacy: PrivacyRepository,
    private val power: PowerRepository,
    private val audit: AppAuditRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SimpleUiState())
    val ui: StateFlow<SimpleUiState> = _ui

    /** Estado vivo para el semaforo de la cabecera, antes de pulsar Analizar. */
    val live: StateFlow<PrivacyState> = MonitoringController.gate { privacy.observe() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivacyState())

    val usageAccess: Boolean get() = audit.isUsageAccessGranted()

    fun analyze() {
        if (_ui.value.analyzing) return
        _ui.update { it.copy(analyzing = true, error = null, progress = "Leyendo camara, microfono y ubicacion…") }
        viewModelScope.launch {
            runCatching {
                val priv = withTimeoutOrNull(3000) { privacy.observe().take(3).toList().last() } ?: PrivacyState()
                _ui.update { it.copy(progress = "Leyendo bateria y descanso del sistema…") }
                val pow = withTimeoutOrNull(3000) { power.observe().first() } ?: PowerState()
                _ui.update { it.copy(progress = "Revisando apps instaladas y sus permisos…") }
                val apps = audit.scan()
                build(priv, pow, apps)
            }.onSuccess { r -> _ui.update { it.copy(analyzing = false, progress = null, report = r) } }
                .onFailure { e -> _ui.update { it.copy(analyzing = false, progress = null, error = e.message ?: e.toString()) } }
        }
    }

    private fun build(priv: PrivacyState, pow: PowerState, apps: List<AuditedApp>): SimpleReport {
        val now = System.currentTimeMillis()
        val usageOk = audit.isUsageAccessGranted()
        val findings = mutableListOf<SimpleFinding>()

        // --- Camara
        val camUsers = probableUsers(apps, now) { it.camera }
        findings += if (priv.camera.inUse) SimpleFinding(
            title = "La camara esta ENCENDIDA ahora mismo",
            detail = buildString {
                append("Alguna app la tiene abierta en este momento. ")
                if (camUsers.isNotEmpty()) append("Probablemente: ${camUsers.joinToString { it.label }}. ")
                else if (!usageOk) append("Activa 'Acceso a datos de uso' para que pueda decirte cual. ")
                append("Si no estas usando la camara, cierra todo y vuelve a analizar.")
            },
            verdict = Verdict.ALERT,
            apps = camUsers,
        ) else SimpleFinding("Camara apagada", "Ninguna app la esta usando ahora.", Verdict.OK)

        // --- Microfono
        val micUsers = probableUsers(apps, now) { it.microphone }
        findings += if (priv.microphone.inUse) SimpleFinding(
            title = "El microfono esta ESCUCHANDO ahora mismo",
            detail = buildString {
                append("Hay ${priv.microphone.activeRecordings} grabacion(es) activas en el sistema")
                if (priv.microphone.sources.isNotEmpty()) append(" (${priv.microphone.sources.joinToString()})")
                append(". ")
                if (micUsers.isNotEmpty()) append("Probablemente: ${micUsers.joinToString { it.label }}. ")
                else if (!usageOk) append("Activa 'Acceso a datos de uso' para que pueda decirte cual. ")
                append("Si no estas en una llamada ni grabando, es raro.")
            },
            verdict = Verdict.ALERT,
            apps = micUsers,
        ) else SimpleFinding("Microfono en silencio", "No hay ninguna grabacion activa.", Verdict.OK)

        // --- Ubicacion
        val bgLoc = apps.filter { !it.isSystem && it.backgroundLocation }
        findings += when {
            !priv.location.locationEnabled -> SimpleFinding("Ubicacion apagada", "Ninguna app puede saber donde estas hasta que la actives.", Verdict.OK)
            bgLoc.isNotEmpty() -> SimpleFinding(
                "Ubicacion encendida y ${bgLoc.size} app(s) pueden rastrearte en segundo plano",
                "Estas apps tienen permiso de ubicacion 'siempre', incluso con la pantalla apagada: ${bgLoc.joinToString { it.label }}. Si no lo necesitan (mapas, clima), cambialo a 'solo mientras se usa'.",
                Verdict.WARN,
                bgLoc,
            )
            else -> SimpleFinding("Ubicacion encendida, bajo control", "Ninguna app no-sistema puede usarla en segundo plano.", Verdict.OK)
        }

        // --- Apps sospechosas
        val suspicious = apps.filter { it.suspicious }
        findings += when {
            suspicious.any { it.score >= 5 } -> SimpleFinding(
                "${suspicious.size} app(s) con senales de riesgo",
                "Instaladas fuera de Play Store, sin icono, muy recientes o con poderes especiales, y con acceso a camara, microfono, ubicacion o SMS. Revisa la lista de abajo; si no reconoces alguna, desinstalala.",
                Verdict.ALERT,
            )
            suspicious.isNotEmpty() -> SimpleFinding(
                "${suspicious.size} app(s) que conviene revisar",
                "No son necesariamente virus, pero combinan permisos delicados con un origen o comportamiento poco habitual.",
                Verdict.WARN,
            )
            else -> SimpleFinding("Apps instaladas: sin senales de alarma", "Las ${apps.count { !it.isSystem }} apps que instalaste vienen de la tienda y no tienen combinaciones raras de permisos.", Verdict.OK)
        }

        // --- Accesibilidad / admin (muy usados por malware bancario)
        val a11y = apps.filter { !it.isSystem && it.accessibilityService }
        if (a11y.isNotEmpty()) findings += SimpleFinding(
            "${a11y.size} app(s) controlan tu pantalla por accesibilidad",
            "Un servicio de accesibilidad puede leer todo lo que ves y pulsar por ti. Es legitimo en lectores de pantalla o gestores de contrasenas; en cualquier otra cosa, desconfia: ${a11y.joinToString { it.label }}.",
            Verdict.WARN,
            a11y,
        )

        // --- Energia en una frase
        val sl = pow.sleep
        val pct = sl.deepSleepPercentLastWindow
        findings += when {
            pct == null -> SimpleFinding("Descanso del telefono: sin medir", "Activa el monitor en la vista Pro, desconecta el cable y deja la pantalla apagada unos minutos para saber si algo lo mantiene despierto.", Verdict.OK)
            pct >= 80f -> SimpleFinding("Tu telefono descansa bien", "Con la pantalla apagada el procesador se durmio el %.0f %% del tiempo. Normal.".format(pct), Verdict.OK)
            pct >= 40f -> SimpleFinding("Algo despierta tu telefono de vez en cuando", "Con la pantalla apagada solo durmio el %.0f %% del tiempo. Suele ser sincronizacion agresiva o notificaciones; gasta bateria.".format(pct), Verdict.WARN)
            else -> SimpleFinding("Algo mantiene tu telefono despierto", "Con la pantalla apagada casi no durmio (%.0f %%). Una app lo retiene; se nota en la bateria y en el calor.".format(pct), Verdict.ALERT)
        }
        val temp = pow.battery.temperatureC
        if (temp != null && temp >= 42f) findings += SimpleFinding("El telefono esta caliente (%.0f °C)".format(temp), "Si no estas jugando ni cargando rapido, algo esta consumiendo de mas.", Verdict.WARN)

        val overall = when {
            findings.any { it.verdict == Verdict.ALERT } -> Verdict.ALERT
            findings.any { it.verdict == Verdict.WARN } -> Verdict.WARN
            else -> Verdict.OK
        }
        val headline = when (overall) {
            Verdict.OK -> "Todo en orden"
            Verdict.WARN -> "Nada grave, pero revisa esto"
            Verdict.ALERT -> "Atencion: hay algo que no cuadra"
        }
        return SimpleReport(now, overall, headline, findings, suspicious, apps.size, usageOk)
    }

    /**
     * Apps que, teniendo el permiso, estuvieron en primer plano en los ultimos 20 s o lo estan ahora.
     * Sin acceso de uso devolvemos vacio: mejor no acusar a nadie sin evidencia.
     */
    private fun probableUsers(apps: List<AuditedApp>, now: Long, hasPermission: (AuditedApp) -> Boolean): List<AuditedApp> =
        apps.filter { hasPermission(it) && (it.inForegroundNow || (it.lastForegroundEpochMs ?: 0L) > now - 20_000L) }
            .sortedByDescending { it.lastForegroundEpochMs ?: 0L }
            .take(3)

    fun usageAccessIntent() = audit.usageAccessIntent()
    fun privacyDashboardIntent() = audit.privacyDashboardIntent()
    fun appInfoIntent(pkg: String) = audit.appInfoIntent(pkg)
    fun uninstallIntent(pkg: String) = audit.uninstallIntent(pkg)
}
