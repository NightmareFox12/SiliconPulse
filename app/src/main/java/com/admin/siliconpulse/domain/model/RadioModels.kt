package com.admin.siliconpulse.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CellularState(
    val available: Boolean = false,
    val permissionGranted: Boolean = false,
    val operatorName: String? = null,
    val simOperator: String? = null,
    val networkTypeName: String? = null,
    val generation: String? = null,
    val signalDbm: Int? = null,
    val signalLevel: Int? = null,
    val asuLevel: Int? = null,
    val roaming: Boolean = false,
    val dataState: String? = null,
    val cellIdentity: String? = null,
    val nrState: String? = null,
)

@Serializable
data class WifiState(
    val enabled: Boolean = false,
    val connected: Boolean = false,
    val ssid: String? = null,
    val bssid: String? = null,
    val rssiDbm: Int? = null,
    val signalLevel: Int? = null,
    val frequencyMhz: Int? = null,
    val band: String? = null,
    val linkSpeedMbps: Int? = null,
    val txLinkSpeedMbps: Int? = null,
    val rxLinkSpeedMbps: Int? = null,
    val standard: String? = null,
    val ipAddress: String? = null,
    val downstreamKbps: Int? = null,
    val upstreamKbps: Int? = null,
    val metered: Boolean? = null,
)

@Serializable
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val type: String,
    val bondState: String,
    val connected: Boolean,
)

@Serializable
data class BluetoothState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val permissionGranted: Boolean = false,
    val adapterName: String? = null,
    val devices: List<BluetoothDeviceInfo> = emptyList(),
    val connectedProfiles: List<String> = emptyList(),
)

@Serializable
data class RadioState(
    val cellular: CellularState = CellularState(),
    val wifi: WifiState = WifiState(),
    val bluetooth: BluetoothState = BluetoothState(),
    val activeTransport: String? = null,
    val airplaneMode: Boolean = false,
)
