package com.admin.siliconpulse.domain.model

import kotlinx.serialization.Serializable

/** Frecuencia de muestreo expuesta al usuario; se traduce a constantes de SensorManager en la capa de datos. */
enum class SamplingRate(val label: String) {
    UI("UI (~16 Hz)"),
    GAME("Game (~50 Hz)"),
    FASTEST("Fastest");
}

/** Canal de sensor que el osciloscopio puede graficar. */
enum class SensorChannel(val label: String, val unit: String, val axes: Int) {
    ACCELEROMETER("Acelerometro", "m/s²", 3),
    LINEAR_ACCELERATION("Aceleracion lineal", "m/s²", 3),
    GYROSCOPE("Giroscopio", "rad/s", 3),
    MAGNETOMETER("Magnetometro", "µT", 3),
    LIGHT("Luz ambiental", "lx", 1),
    PROXIMITY("Proximidad", "cm", 1),
    PRESSURE("Barometro", "hPa", 1),
    STEP_COUNTER("Podometro HW", "pasos", 1);
}

/** Una muestra cruda del sensor. Los ejes no usados quedan en 0. */
data class SensorSample(
    val channel: SensorChannel,
    val timestampNanos: Long,
    val x: Float,
    val y: Float = 0f,
    val z: Float = 0f,
    val accuracy: Int = 0,
)

/** Descripcion estatica de un sensor fisico reportado por el HAL. */
@Serializable
data class SensorDescriptor(
    val name: String,
    val vendor: String,
    val type: Int,
    val typeName: String,
    val version: Int,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    val minDelayUs: Int,
    val maxDelayUs: Int,
    val isWakeUp: Boolean,
    val reportingMode: String,
)

/** Snapshot de un canal para el reporte exportado. */
@Serializable
data class SensorChannelSnapshot(
    val channel: String,
    val unit: String,
    val available: Boolean,
    val lastX: Float,
    val lastY: Float,
    val lastZ: Float,
    val sampleRateHz: Float,
    val accuracy: Int,
)
