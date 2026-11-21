package com.admin.siliconpulse.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.admin.siliconpulse.domain.model.SamplingRate
import com.admin.siliconpulse.domain.model.SensorChannel
import com.admin.siliconpulse.domain.model.SensorDescriptor
import com.admin.siliconpulse.domain.model.SensorSample
import com.admin.siliconpulse.domain.repository.SensorRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

class SensorRepositoryImpl(context: Context) : SensorRepository {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /**
     * Hilo dedicado para recibir eventos del HAL. Mantener los callbacks fuera del main
     * thread evita jank en Compose cuando se muestrea a SENSOR_DELAY_GAME o FASTEST.
     */
    private val sensorThread = HandlerThread("silicon-pulse-sensors").apply { start() }
    private val sensorHandler = Handler(sensorThread.looper)

    override fun listSensors(): List<SensorDescriptor> =
        sensorManager.getSensorList(Sensor.TYPE_ALL).map { it.toDescriptor() }

    override fun isAvailable(channel: SensorChannel): Boolean =
        sensorManager.getDefaultSensor(channel.toSensorType()) != null

    override fun samples(channel: SensorChannel, rate: SamplingRate): Flow<SensorSample> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(channel.toSensorType())
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values
                trySend(
                    SensorSample(
                        channel = channel,
                        timestampNanos = event.timestamp,
                        x = v.getOrElse(0) { 0f },
                        y = v.getOrElse(1) { 0f },
                        z = v.getOrElse(2) { 0f },
                        accuracy = event.accuracy,
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = sensorManager.registerListener(listener, sensor, rate.toDelay(), sensorHandler)
        if (!registered) close(IllegalStateException("No se pudo registrar ${channel.label}"))
        awaitClose { sensorManager.unregisterListener(listener) }
    }.buffer(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun SamplingRate.toDelay(): Int = when (this) {
        SamplingRate.UI -> SensorManager.SENSOR_DELAY_UI
        SamplingRate.GAME -> SensorManager.SENSOR_DELAY_GAME
        SamplingRate.FASTEST -> SensorManager.SENSOR_DELAY_FASTEST
    }

    private fun SensorChannel.toSensorType(): Int = when (this) {
        SensorChannel.ACCELEROMETER -> Sensor.TYPE_ACCELEROMETER
        SensorChannel.LINEAR_ACCELERATION -> Sensor.TYPE_LINEAR_ACCELERATION
        SensorChannel.GYROSCOPE -> Sensor.TYPE_GYROSCOPE
        SensorChannel.MAGNETOMETER -> Sensor.TYPE_MAGNETIC_FIELD
        SensorChannel.LIGHT -> Sensor.TYPE_LIGHT
        SensorChannel.PROXIMITY -> Sensor.TYPE_PROXIMITY
        SensorChannel.PRESSURE -> Sensor.TYPE_PRESSURE
        SensorChannel.STEP_COUNTER -> Sensor.TYPE_STEP_COUNTER
    }

    private fun Sensor.toDescriptor() = SensorDescriptor(
        name = name,
        vendor = vendor,
        type = type,
        typeName = stringType,
        version = version,
        maxRange = maximumRange,
        resolution = resolution,
        powerMa = power,
        minDelayUs = minDelay,
        maxDelayUs = maxDelay,
        isWakeUp = isWakeUpSensor,
        reportingMode = when (reportingMode) {
            Sensor.REPORTING_MODE_CONTINUOUS -> "continuous"
            Sensor.REPORTING_MODE_ON_CHANGE -> "on_change"
            Sensor.REPORTING_MODE_ONE_SHOT -> "one_shot"
            Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "special_trigger"
            else -> "unknown"
        },
    )
}
