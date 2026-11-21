package com.admin.siliconpulse.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val board: String,
    val hardware: String,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildId: String,
    val fingerprint: String,
    val kernelVersion: String,
)

/** Snapshot completo del sistema que se exporta como JSON. */
@Serializable
data class DiagnosticReport(
    val generatedAtEpochMs: Long,
    val generatedAtIso: String,
    val appVersion: String,
    val device: DeviceInfo,
    val sensorsAvailable: List<SensorDescriptor>,
    val sensorSnapshots: List<SensorChannelSnapshot>,
    val privacy: PrivacyState,
    val radios: RadioState,
    val power: PowerState,
    val notes: List<String>,
)
