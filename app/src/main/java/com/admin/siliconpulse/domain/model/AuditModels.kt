package com.admin.siliconpulse.domain.model

import kotlinx.serialization.Serializable

/** Una app instalada vista desde la optica de privacidad. */
@Serializable
data class AuditedApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    /** Paquete que la instalo (com.android.vending = Play Store). null = desconocido/sideload. */
    val installer: String?,
    val fromPlayStore: Boolean,
    val hasLauncherIcon: Boolean,
    val firstInstallEpochMs: Long,
    val lastUpdateEpochMs: Long,
    val camera: Boolean,
    val microphone: Boolean,
    val location: Boolean,
    val backgroundLocation: Boolean,
    val readSms: Boolean,
    val accessibilityService: Boolean,
    val deviceAdmin: Boolean,
    /** Ultimo instante en primer plano segun UsageStats (null si no hay acceso o nunca). */
    val lastForegroundEpochMs: Long? = null,
    val inForegroundNow: Boolean = false,
    val reasons: List<String> = emptyList(),
    val score: Int = 0,
) {
    val suspicious: Boolean get() = score >= 3
}

enum class Verdict { OK, WARN, ALERT }

/** Resultado de "Analizar mi telefono" en lenguaje llano. */
data class SimpleFinding(
    val title: String,
    val detail: String,
    val verdict: Verdict,
    /** Apps relacionadas con este hallazgo (p. ej. probables usuarias de la camara). */
    val apps: List<AuditedApp> = emptyList(),
)

data class SimpleReport(
    val generatedAtEpochMs: Long,
    val overall: Verdict,
    val headline: String,
    val findings: List<SimpleFinding>,
    val suspiciousApps: List<AuditedApp>,
    val totalApps: Int,
    val usageAccessGranted: Boolean,
)
