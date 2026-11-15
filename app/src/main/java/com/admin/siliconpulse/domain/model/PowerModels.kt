package com.admin.siliconpulse.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BatteryState(
    val levelPercent: Int = -1,
    val status: String = "unknown",
    val health: String = "unknown",
    val plugged: String = "none",
    val temperatureC: Float? = null,
    val voltageMv: Int? = null,
    /** Corriente instantanea en mA. Positivo = cargando, negativo = descargando (convencion Android). */
    val currentNowMa: Float? = null,
    val currentAverageMa: Float? = null,
    val chargeCounterMah: Float? = null,
    val energyCounterNwh: Long? = null,
    val technology: String? = null,
    val cycleCount: Int? = null,
    val chargingTimeRemainingMin: Long? = null,
    val isBatterySaver: Boolean = false,
)

@Serializable
data class ThermalState(
    val status: String = "none",
    val statusCode: Int = 0,
    val headroom: Float? = null,
)

@Serializable
data class MemoryState(
    val totalMb: Long = 0,
    val availableMb: Long = 0,
    val lowMemory: Boolean = false,
    val thresholdMb: Long = 0,
    val appPssMb: Long = 0,
)

@Serializable
data class CpuState(
    val cores: Int = 0,
    /** Frecuencias actuales por core en MHz, leidas de sysfs si el kernel lo expone. */
    val currentFreqMhz: List<Int> = emptyList(),
    val maxFreqMhz: List<Int> = emptyList(),
    val socModel: String? = null,
    val socManufacturer: String? = null,
    val abi: String? = null,
)

/**
 * Diagnostico de deep sleep. Se basa en la diferencia entre
 * SystemClock.elapsedRealtime() (incluye sueno) y SystemClock.uptimeMillis() (excluye sueno).
 */
@Serializable
data class SleepState(
    val uptimeMs: Long = 0,
    val elapsedRealtimeMs: Long = 0,
    /** Tiempo total que el SoC paso en suspend desde el boot. */
    val deepSleepTotalMs: Long = 0,
    /** Porcentaje de deep sleep desde el boot. */
    val deepSleepPercentSinceBoot: Float = 0f,
    /** Porcentaje de deep sleep en la ultima ventana de observacion (pantalla apagada). */
    val deepSleepPercentLastWindow: Float? = null,
    val lastWindowDurationMs: Long? = null,
    val screenOn: Boolean = true,
    val interactive: Boolean = true,
    val deviceIdleMode: Boolean = false,
    val lightIdleMode: Boolean = false,
    val powerSaveMode: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false,
    val wakeLockHeldByApp: Boolean = false,
    val verdict: String = "Sin datos",
)

@Serializable
data class PowerState(
    val battery: BatteryState = BatteryState(),
    val thermal: ThermalState = ThermalState(),
    val memory: MemoryState = MemoryState(),
    val cpu: CpuState = CpuState(),
    val sleep: SleepState = SleepState(),
)
