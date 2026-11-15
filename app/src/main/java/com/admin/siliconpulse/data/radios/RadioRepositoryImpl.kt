package com.admin.siliconpulse.data.radios

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.admin.siliconpulse.domain.model.BluetoothDeviceInfo
import com.admin.siliconpulse.domain.model.BluetoothState
import com.admin.siliconpulse.domain.model.CellularState
import com.admin.siliconpulse.domain.model.RadioState
import com.admin.siliconpulse.domain.model.WifiState
import com.admin.siliconpulse.domain.repository.RadioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RadioRepositoryImpl(private val context: Context) : RadioRepository {

    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?

    override fun observe(): Flow<RadioState> = callbackFlow {
        @Volatile var wifiInfoFromCallback: WifiInfo? = null
        @Volatile var wifiCaps: NetworkCapabilities? = null

        // En Android 12+ el SSID solo llega si el callback pide explicitamente info de ubicacion.
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    wifiCaps = caps
                    wifiInfoFromCallback = caps.transportInfo as? WifiInfo
                }

                override fun onLost(network: Network) {
                    wifiCaps = null; wifiInfoFromCallback = null
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    wifiCaps = caps
                }

                override fun onLost(network: Network) {
                    wifiCaps = null
                }
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching { connectivity.registerNetworkCallback(request, callback) }

        val poller = launch(Dispatchers.IO) {
            while (true) {
                val wifiInfo = wifiInfoFromCallback ?: legacyWifiInfo()
                trySend(
                    RadioState(
                        cellular = readCellular(),
                        wifi = readWifi(wifiInfo, wifiCaps),
                        bluetooth = readBluetooth(),
                        activeTransport = activeTransport(),
                        airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
                    )
                )
                delay(2000)
            }
        }
        awaitClose {
            poller.cancel()
            runCatching { connectivity.unregisterNetworkCallback(callback) }
        }
    }.conflate().flowOn(Dispatchers.IO)

    // ------------------------------------------------------------------ Cellular
    private fun readCellular(): CellularState {
        val hasPhone = hasPermission(Manifest.permission.READ_PHONE_STATE)
        val hasLoc = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (telephony.phoneType == TelephonyManager.PHONE_TYPE_NONE) return CellularState(available = false)

        val signal = runCatching { telephony.signalStrength }.getOrNull()
        val primary = signal?.cellSignalStrengths?.firstOrNull()
        val netType = if (hasPhone) runCatching { telephony.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        else TelephonyManager.NETWORK_TYPE_UNKNOWN
        val serviceStateStr = if (hasPhone && hasLoc) runCatching { telephony.serviceState?.toString() }.getOrNull() else null
        val nrState = serviceStateStr?.let { Regex("nrState=([A-Z_]+)").find(it)?.groupValues?.get(1) }
        val cellIdentity = if (hasLoc) runCatching { telephony.allCellInfo?.firstOrNull { it.isRegistered }?.let(::describeCell) }.getOrNull() else null

        return CellularState(
            available = true,
            permissionGranted = hasPhone,
            operatorName = telephony.networkOperatorName.takeIf { it.isNotBlank() },
            simOperator = telephony.simOperatorName.takeIf { it.isNotBlank() },
            networkTypeName = networkTypeName(netType),
            generation = generation(netType, nrState),
            signalDbm = primary?.dbm?.takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE },
            signalLevel = primary?.level,
            asuLevel = primary?.asuLevel?.takeIf { it != 99 && it != Int.MAX_VALUE },
            roaming = telephony.isNetworkRoaming,
            dataState = when (telephony.dataState) {
                TelephonyManager.DATA_CONNECTED -> "connected"
                TelephonyManager.DATA_CONNECTING -> "connecting"
                TelephonyManager.DATA_SUSPENDED -> "suspended"
                else -> "disconnected"
            },
            cellIdentity = cellIdentity,
            nrState = nrState,
        )
    }

    private fun describeCell(info: CellInfo): String = when (info) {
        is CellInfoNr -> "NR ${info.cellIdentity}".substringBefore("mAdditionalPlmns").take(120)
        is CellInfoLte -> { val id = info.cellIdentity; "LTE ci=${id.ci} pci=${id.pci} tac=${id.tac} earfcn=${id.earfcn} bw=${id.bandwidth}kHz" }
        is CellInfoWcdma -> { val id = info.cellIdentity; "WCDMA cid=${id.cid} lac=${id.lac} psc=${id.psc}" }
        is CellInfoGsm -> { val id = info.cellIdentity; "GSM cid=${id.cid} lac=${id.lac} arfcn=${id.arfcn}" }
        else -> info.javaClass.simpleName
    }

    private fun networkTypeName(t: Int) = when (t) {
        TelephonyManager.NETWORK_TYPE_NR -> "NR"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "unknown"
        else -> "type_$t"
    }

    private fun generation(t: Int, nrState: String?) = when {
        t == TelephonyManager.NETWORK_TYPE_NR -> "5G SA"
        nrState == "CONNECTED" -> "5G NSA"
        t == TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        t in setOf(TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_UMTS) -> "3G"
        t in setOf(TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_CDMA) -> "2G"
        else -> null
    }

    // ------------------------------------------------------------------ Wi-Fi
    @Suppress("DEPRECATION")
    private fun legacyWifiInfo(): WifiInfo? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) runCatching { wifiManager.connectionInfo }.getOrNull() else null

    private fun readWifi(info: WifiInfo?, caps: NetworkCapabilities?): WifiState {
        val enabled = wifiManager.isWifiEnabled
        if (info == null || info.networkId == -1 && info.rssi == -127 && info.bssid == null) {
            return WifiState(enabled = enabled, connected = caps != null)
        }
        val ssid = info.ssid?.removeSurrounding("\"")?.takeIf { it != WifiManager.UNKNOWN_SSID && it.isNotBlank() }
        val freq = info.frequency.takeIf { it > 0 }
        return WifiState(
            enabled = enabled,
            connected = true,
            ssid = ssid,
            bssid = info.bssid?.takeIf { it != "02:00:00:00:00:00" },
            rssiDbm = info.rssi.takeIf { it != -127 },
            signalLevel = runCatching { wifiManager.calculateSignalLevel(info.rssi) }.getOrNull(),
            frequencyMhz = freq,
            band = freq?.let { f -> when { f < 3000 -> "2.4 GHz"; f < 5900 -> "5 GHz"; else -> "6 GHz" } },
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
            txLinkSpeedMbps = info.txLinkSpeedMbps.takeIf { it > 0 },
            rxLinkSpeedMbps = info.rxLinkSpeedMbps.takeIf { it > 0 },
            standard = when (info.wifiStandard) {
                android.net.wifi.ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
                android.net.wifi.ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
                android.net.wifi.ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
                android.net.wifi.ScanResult.WIFI_STANDARD_LEGACY -> "Legacy (a/b/g)"
                7 -> "Wi-Fi 7 (802.11be)"
                else -> null
            },
            ipAddress = connectivity.getLinkProperties(connectivity.activeNetwork)?.linkAddresses
                ?.firstOrNull { it.address.hostAddress?.contains('.') == true }?.address?.hostAddress,
            downstreamKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
            upstreamKbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 },
            metered = caps?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) },
        )
    }

    // ------------------------------------------------------------------ Bluetooth
    private fun readBluetooth(): BluetoothState {
        val adapter: BluetoothAdapter = bluetoothManager?.adapter ?: return BluetoothState(supported = false)
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        if (!granted) return BluetoothState(supported = true, enabled = adapter.isEnabled, permissionGranted = false)

        val devices = runCatching {
            adapter.bondedDevices.map { d ->
                BluetoothDeviceInfo(
                    name = d.name ?: "(sin nombre)",
                    address = d.address,
                    type = when (d.type) {
                        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                        BluetoothDevice.DEVICE_TYPE_LE -> "LE"
                        BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                        else -> "Unknown"
                    },
                    bondState = when (d.bondState) {
                        BluetoothDevice.BOND_BONDED -> "bonded"
                        BluetoothDevice.BOND_BONDING -> "bonding"
                        else -> "none"
                    },
                    connected = isDeviceConnected(d),
                )
            }
        }.getOrDefault(emptyList())

        val profiles = buildList {
            if (adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED) add("HEADSET")
            if (adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED) add("A2DP")
            if (adapter.getProfileConnectionState(BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED) add("GATT")
            if (runCatching { bluetoothManager.getConnectedDevices(BluetoothProfile.GATT).isNotEmpty() }.getOrDefault(false)) add("GATT_CLIENT")
        }
        return BluetoothState(
            supported = true,
            enabled = adapter.isEnabled,
            permissionGranted = true,
            adapterName = runCatching { adapter.name }.getOrNull(),
            devices = devices,
            connectedProfiles = profiles,
        )
    }

    /** `BluetoothDevice.isConnected()` es @hide; reflexion con fallback a false. */
    private fun isDeviceConnected(d: BluetoothDevice): Boolean = runCatching {
        d.javaClass.getMethod("isConnected").invoke(d) as Boolean
    }.getOrDefault(false)

    // ------------------------------------------------------------------ Misc
    private fun activeTransport(): String? {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork ?: return null) ?: return null
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "OTHER"
        }
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}
