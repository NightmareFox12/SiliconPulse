package com.admin.siliconpulse.data.audit

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import com.admin.siliconpulse.domain.model.AuditedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inventario de apps desde la optica de privacidad.
 *
 * Lo que Android permite a una app normal:
 *  - Ver que permisos tiene concedidos cada paquete (QUERY_ALL_PACKAGES).
 *  - Saber quien instalo cada paquete (Play Store vs sideload).
 *  - Con "Acceso a datos de uso" (lo concede el usuario en Ajustes): que app estuvo
 *    en primer plano y cuando. Eso permite correlacionar "la camara se ocupo a las 10:31"
 *    con "AmarXP paso a primer plano a las 10:31".
 * Lo que NO permite: preguntar directamente que paquete tiene abierta la camara (WATCH_APPOPS,
 * solo sistema). Para eso se enlaza al Panel de privacidad del sistema.
 */
class AppAuditRepository(private val context: Context) {

    private val pm = context.packageManager
    private val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    fun isUsageAccessGranted(): Boolean {
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * Panel de privacidad del sistema (Android 12+): historial oficial de camara/micro/ubicacion por app.
     * Algunos OEM (p. ej. Infinix/XOS) protegen REVIEW_PERMISSION_USAGE con un permiso de sistema y lanzan
     * SecurityException, asi que se intenta y, si falla, se abre la pantalla general de Privacidad.
     */
    fun openPrivacyDashboard(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val review = Intent("android.intent.action.REVIEW_PERMISSION_USAGE")
            if (runCatching { context.startActivity(review); true }.getOrDefault(false)) return true
        }
        return runCatching { context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS)); true }.getOrDefault(false)
    }

    fun appInfoIntent(pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))

    fun uninstallIntent(pkg: String): Intent =
        Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg"))

    suspend fun scan(): List<AuditedApp> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val foreground = foregroundInfo(now)
        val a11y = enabledAccessibilityPackages()
        val admins = activeAdminPackages()
        val self = context.packageName

        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .asSequence()
            .filter { it.packageName != self }
            .map { pi -> toAudited(pi, now, foreground[pi.packageName], a11y, admins) }
            .filter { it.camera || it.microphone || it.location || it.readSms || it.accessibilityService || it.deviceAdmin || !it.isSystem }
            .sortedWith(compareByDescending<AuditedApp> { it.score }.thenBy { it.label.lowercase() })
            .toList()
    }

    private fun toAudited(
        pi: PackageInfo,
        now: Long,
        fg: ForegroundInfo?,
        a11y: Set<String>,
        admins: Set<String>,
    ): AuditedApp {
        val ai = pi.applicationInfo
        val isSystem = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val granted = grantedPermissions(pi)
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getInstallSourceInfo(pi.packageName).installingPackageName
            else @Suppress("DEPRECATION") pm.getInstallerPackageName(pi.packageName)
        }.getOrNull()
        val fromPlay = installer == "com.android.vending"
        val hasLauncher = pm.getLaunchIntentForPackage(pi.packageName) != null
        val label = ai?.let { pm.getApplicationLabel(it).toString() } ?: pi.packageName

        val camera = Manifest.permission.CAMERA in granted
        val mic = Manifest.permission.RECORD_AUDIO in granted
        val loc = Manifest.permission.ACCESS_FINE_LOCATION in granted || Manifest.permission.ACCESS_COARSE_LOCATION in granted
        val bgLoc = Manifest.permission.ACCESS_BACKGROUND_LOCATION in granted
        val sms = Manifest.permission.READ_SMS in granted || Manifest.permission.RECEIVE_SMS in granted
        val sensitive = camera || mic || loc || sms
        val ageDays = (now - pi.firstInstallTime) / 86_400_000L

        val reasons = mutableListOf<String>()
        var score = 0
        if (!isSystem && !fromPlay && sensitive) { score += 3; reasons += "Instalada fuera de Play Store (${installer ?: "APK manual"}) y con acceso a ${sensitiveList(camera, mic, loc, sms)}" }
        if (!isSystem && !hasLauncher && sensitive) { score += 2; reasons += "No tiene icono en el launcher pero pide ${sensitiveList(camera, mic, loc, sms)}" }
        if (!isSystem && ageDays <= 7 && sensitive) { score += 2; reasons += "Instalada hace ${if (ageDays == 0L) "menos de un dia" else "$ageDays dias"}" }
        if (!isSystem && bgLoc) { score += 2; reasons += "Puede leer tu ubicacion en segundo plano" }
        if (!isSystem && camera && mic && loc) { score += 1; reasons += "Tiene camara, microfono y ubicacion a la vez" }
        if (!isSystem && pi.packageName in a11y) { score += 3; reasons += "Servicio de accesibilidad activo: puede leer la pantalla y pulsar por ti" }
        if (!isSystem && pi.packageName in admins) { score += 2; reasons += "Es administrador del dispositivo: dificil de desinstalar" }
        if (!isSystem && sms) { score += 1; reasons += "Puede leer tus SMS (codigos bancarios)" }

        return AuditedApp(
            packageName = pi.packageName,
            label = label,
            isSystem = isSystem,
            installer = installer,
            fromPlayStore = fromPlay,
            hasLauncherIcon = hasLauncher,
            firstInstallEpochMs = pi.firstInstallTime,
            lastUpdateEpochMs = pi.lastUpdateTime,
            camera = camera,
            microphone = mic,
            location = loc,
            backgroundLocation = bgLoc,
            readSms = sms,
            accessibilityService = pi.packageName in a11y,
            deviceAdmin = pi.packageName in admins,
            lastForegroundEpochMs = fg?.lastForeground,
            inForegroundNow = fg?.inForeground == true,
            reasons = reasons,
            score = score,
        )
    }

    private fun sensitiveList(cam: Boolean, mic: Boolean, loc: Boolean, sms: Boolean) =
        listOfNotNull(if (cam) "camara" else null, if (mic) "microfono" else null, if (loc) "ubicacion" else null, if (sms) "SMS" else null).joinToString(", ")

    private fun grantedPermissions(pi: PackageInfo): Set<String> {
        val names = pi.requestedPermissions ?: return emptySet()
        val flags = pi.requestedPermissionsFlags ?: return emptySet()
        return names.indices
            .filter { (flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0 }
            .map { names[it] }
            .toSet()
    }

    private data class ForegroundInfo(val lastForeground: Long, val inForeground: Boolean)

    /** Ultimo primer plano por paquete en los ultimos 30 min; vacio si no hay acceso de uso. */
    private fun foregroundInfo(now: Long): Map<String, ForegroundInfo> {
        if (!isUsageAccessGranted()) return emptyMap()
        val result = HashMap<String, ForegroundInfo>()
        val events = runCatching { usage.queryEvents(now - 30 * 60_000L, now) }.getOrNull() ?: return emptyMap()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> result[e.packageName] = ForegroundInfo(e.timeStamp, true)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    result[e.packageName]?.let { result[e.packageName] = it.copy(inForeground = false) }
            }
        }
        return result
    }

    private fun enabledAccessibilityPackages(): Set<String> {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
            .toSet()
    }

    private fun activeAdminPackages(): Set<String> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.activeAdmins?.map(ComponentName::getPackageName)?.toSet() ?: emptySet()
    }
}
