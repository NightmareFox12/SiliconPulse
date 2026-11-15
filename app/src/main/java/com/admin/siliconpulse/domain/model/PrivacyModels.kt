package com.admin.siliconpulse.domain.model

import kotlinx.serialization.Serializable

/** Estado observable del microfono. */
@Serializable
data class MicrophoneState(
    /** Grabaciones activas reportadas por AudioManager (incluye otras apps, anonimizadas). */
    val activeRecordings: Int = 0,
    /** Fuentes de audio en uso (MIC, VOICE_COMMUNICATION, etc.). */
    val sources: List<String> = emptyList(),
    /** Interruptor global de privacidad del microfono (Android 12+). */
    val hardwareMuted: Boolean = false,
    /** Paquetes detectados por AppOps si el sistema lo permite (normalmente solo la propia app). */
    val appOpsPackages: List<String> = emptyList(),
) {
    val inUse: Boolean get() = activeRecordings > 0 || appOpsPackages.isNotEmpty()
}

/** Estado observable de las camaras. */
@Serializable
data class CameraState(
    val totalCameras: Int = 0,
    /** IDs de camara que el sistema reporta como no disponibles (en uso por alguna app). */
    val unavailableIds: List<String> = emptyList(),
    val hardwareMuted: Boolean = false,
    val appOpsPackages: List<String> = emptyList(),
) {
    val inUse: Boolean get() = unavailableIds.isNotEmpty() || appOpsPackages.isNotEmpty()
}

@Serializable
data class LocationState(
    val locationEnabled: Boolean = false,
    val gpsProviderEnabled: Boolean = false,
    val networkProviderEnabled: Boolean = false,
    val permissionGranted: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val speedMps: Float? = null,
    val provider: String? = null,
    val fixAgeSeconds: Long? = null,
    val satellitesVisible: Int = 0,
    val satellitesUsedInFix: Int = 0,
)

@Serializable
data class PrivacyState(
    val microphone: MicrophoneState = MicrophoneState(),
    val camera: CameraState = CameraState(),
    val location: LocationState = LocationState(),
    /** Si AppOps permitio vigilar ops de terceros (requiere WATCH_APPOPS, normalmente no). */
    val appOpsWatchAvailable: Boolean = false,
)
