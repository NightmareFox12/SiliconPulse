package com.admin.siliconpulse.data.privacy

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorPrivacyManager
import android.hardware.camera2.CameraManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.admin.siliconpulse.domain.model.CameraState
import com.admin.siliconpulse.domain.model.LocationState
import com.admin.siliconpulse.domain.model.MicrophoneState
import com.admin.siliconpulse.domain.model.PrivacyState
import com.admin.siliconpulse.domain.repository.PrivacyRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class PrivacyRepositoryImpl(private val context: Context) : PrivacyRepository {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    override fun observe(): Flow<PrivacyState> = callbackFlow {
        var state = PrivacyState(
            microphone = readMicrophone(),
            camera = CameraState(totalCameras = safeCameraCount()),
            location = readLocation(null),
        )
        fun push() = trySend(state)
        push()

        // --- Microfono: AudioManager reporta todas las grabaciones activas del sistema.
        val recordingCallback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                state = state.copy(microphone = readMicrophone(configs).copy(appOpsPackages = state.microphone.appOpsPackages))
                push()
            }
        }
        audioManager.registerAudioRecordingCallback(recordingCallback, mainHandler)

        // --- Camara: cuando otra app abre una camara, el sistema la marca como no disponible.
        val unavailable = linkedSetOf<String>()
        val cameraCallback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                unavailable.remove(cameraId)
                state = state.copy(camera = state.camera.copy(unavailableIds = unavailable.toList()))
                push()
            }

            override fun onCameraUnavailable(cameraId: String) {
                unavailable.add(cameraId)
                state = state.copy(camera = state.camera.copy(unavailableIds = unavailable.toList()))
                push()
            }
        }
        runCatching { cameraManager.registerAvailabilityCallback(cameraCallback, mainHandler) }

        // --- AppOps: solo funciona para terceros con WATCH_APPOPS (signature). Lo intentamos y reportamos.
        val micPkgs = linkedSetOf<String>()
        val camPkgs = linkedSetOf<String>()
        val appOpsListener = AppOpsManager.OnOpActiveChangedListener { op, _, packageName, active ->
            val set = if (op == AppOpsManager.OPSTR_RECORD_AUDIO) micPkgs else camPkgs
            if (active) set.add(packageName) else set.remove(packageName)
            state = state.copy(
                microphone = state.microphone.copy(appOpsPackages = micPkgs.toList()),
                camera = state.camera.copy(appOpsPackages = camPkgs.toList()),
            )
            push()
        }
        val appOpsOk = runCatching {
            appOps.startWatchingActive(
                arrayOf(AppOpsManager.OPSTR_RECORD_AUDIO, AppOpsManager.OPSTR_CAMERA),
                mainExecutor,
                appOpsListener,
            )
            true
        }.getOrDefault(false)
        state = state.copy(appOpsWatchAvailable = appOpsOk)
        push()

        // --- Ubicacion
        var lastLocation: Location? = null
        var satsVisible = 0
        var satsUsed = 0
        val locationListener = LocationListener { loc ->
            lastLocation = loc
            state = state.copy(location = readLocation(loc).copy(satellitesVisible = satsVisible, satellitesUsedInFix = satsUsed))
            push()
        }
        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                satsVisible = status.satelliteCount
                satsUsed = (0 until status.satelliteCount).count { status.usedInFix(it) }
                state = state.copy(location = state.location.copy(satellitesVisible = satsVisible, satellitesUsedInFix = satsUsed))
                push()
            }
        }
        val hasFine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (hasFine) {
            runCatching {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.requestLocationUpdates(provider, 2000L, 0f, locationListener, Looper.getMainLooper())
                    }
                }
                locationManager.registerGnssStatusCallback(mainExecutor, gnssCallback)
            }
            state = state.copy(location = readLocation(lastLocation))
            push()
        }

        // --- Ticker para refrescar edad del fix y toggles de hardware.
        val ticker = launch {
            while (true) {
                delay(2000)
                state = state.copy(
                    microphone = state.microphone.copy(hardwareMuted = isSensorMuted(SENSOR_MICROPHONE)),
                    camera = state.camera.copy(hardwareMuted = isSensorMuted(SENSOR_CAMERA)),
                    location = readLocation(lastLocation).copy(satellitesVisible = satsVisible, satellitesUsedInFix = satsUsed),
                )
                push()
            }
        }

        awaitClose {
            ticker.cancel()
            audioManager.unregisterAudioRecordingCallback(recordingCallback)
            runCatching { cameraManager.unregisterAvailabilityCallback(cameraCallback) }
            if (appOpsOk) runCatching { appOps.stopWatchingActive(appOpsListener) }
            if (hasFine) {
                runCatching { locationManager.removeUpdates(locationListener) }
                runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
            }
        }
    }.conflate()

    private fun readMicrophone(
        configs: List<AudioRecordingConfiguration> = audioManager.activeRecordingConfigurations,
    ): MicrophoneState = MicrophoneState(
        activeRecordings = configs.size,
        sources = configs.map { sourceName(it.clientAudioSource) },
        hardwareMuted = isSensorMuted(SENSOR_MICROPHONE),
    )

    private fun readLocation(loc: Location?): LocationState = LocationState(
        locationEnabled = locationManager.isLocationEnabled,
        gpsProviderEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false),
        networkProviderEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false),
        permissionGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
        latitude = loc?.latitude,
        longitude = loc?.longitude,
        accuracyMeters = loc?.takeIf { it.hasAccuracy() }?.accuracy,
        altitudeMeters = loc?.takeIf { it.hasAltitude() }?.altitude,
        speedMps = loc?.takeIf { it.hasSpeed() }?.speed,
        provider = loc?.provider,
        fixAgeSeconds = loc?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000_000L },
    )

    private fun safeCameraCount(): Int = runCatching { cameraManager.cameraIdList.size }.getOrDefault(0)

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /**
     * Los toggles de privacidad (Android 12+) solo exponen `supportsSensorToggle` como API publica.
     * `isSensorPrivacyEnabled` es @SystemApi; lo invocamos por reflexion y degradamos a `false` si falla.
     */
    private fun isSensorMuted(sensor: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            val spm = context.getSystemService(SensorPrivacyManager::class.java) ?: return false
            if (!spm.supportsSensorToggle(sensor)) return false
            val m = spm.javaClass.getMethod("isSensorPrivacyEnabled", Int::class.javaPrimitiveType)
            m.invoke(spm, sensor) as Boolean
        }.getOrDefault(false)
    }

    private fun sourceName(src: Int) = when (src) {
        MediaRecorder.AudioSource.MIC -> "MIC"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
        MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
        MediaRecorder.AudioSource.VOICE_PERFORMANCE -> "VOICE_PERFORMANCE"
        MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
        else -> "SOURCE_$src"
    }

    private companion object {
        // SensorPrivacyManager.Sensors.MICROPHONE / CAMERA
        const val SENSOR_MICROPHONE = 1
        const val SENSOR_CAMERA = 2
    }
}
