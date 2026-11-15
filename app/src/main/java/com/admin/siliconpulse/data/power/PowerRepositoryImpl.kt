package com.admin.siliconpulse.data.power

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import com.admin.siliconpulse.domain.model.BatteryState
import com.admin.siliconpulse.domain.model.CpuState
import com.admin.siliconpulse.domain.model.MemoryState
import com.admin.siliconpulse.domain.model.PowerState
import com.admin.siliconpulse.domain.model.SleepState
import com.admin.siliconpulse.domain.model.ThermalState
import com.admin.siliconpulse.domain.repository.PowerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

class PowerRepositoryImpl(private val context: Context) : PowerRepository {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val cpuCount = Runtime.getRuntime().availableProcessors()
    private val maxFreqs: List<Int> by lazy { (0 until cpuCount).map { readSysInt("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq") / 1000 } }

    override fun observe(): Flow<PowerState> = callbackFlow {
        @Volatile var batteryIntent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                batteryIntent = intent
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val poller = launch(Dispatchers.IO) {
            while (true) {
                trySend(
                    PowerState(
                        battery = readBattery(batteryIntent),
                        thermal = readThermal(),
                        memory = readMemory(),
                        cpu = readCpu(),
                        sleep = readSleep(),
                    )
                )
                delay(1000)
            }
        }
        awaitClose {
            poller.cancel()
            runCatching { context.unregisterReceiver(receiver) }
        }
    }.conflate().flowOn(Dispatchers.IO)

    private fun readBattery(intent: Intent?): BatteryState {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val currentNowUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentAvgUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val chargeUah = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val energyNwh = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        val remaining = runCatching { batteryManager.computeChargeTimeRemaining() }.getOrDefault(-1L)

        return BatteryState(
            levelPercent = pct,
            status = when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                else -> "unknown"
            },
            health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
                else -> "unknown"
            },
            plugged = when (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                8 -> "dock"
                else -> "none"
            },
            temperatureC = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f },
            voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE },
            currentNowMa = currentNowUa.takeIf { it != Int.MIN_VALUE }?.let { normalizeCurrentMa(it) },
            currentAverageMa = currentAvgUa.takeIf { it != Int.MIN_VALUE && it != 0 }?.let { normalizeCurrentMa(it) },
            chargeCounterMah = chargeUah.takeIf { it != Int.MIN_VALUE }?.let { it / 1000f },
            energyCounterNwh = energyNwh.takeIf { it != Long.MIN_VALUE },
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            cycleCount = if (Build.VERSION.SDK_INT >= 34) intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 } else null,
            chargingTimeRemainingMin = remaining.takeIf { it > 0 }?.let { it / 60_000 },
            isBatterySaver = powerManager.isPowerSaveMode,
        )
    }

    /**
     * Algunos OEM reportan µA y otros mA en CURRENT_NOW. Heuristica: si |valor| > 20000
     * asumimos µA. El signo lo mantenemos tal cual (positivo = entra carga).
     */
    private fun normalizeCurrentMa(raw: Int): Float = if (abs(raw) > 20_000) raw / 1000f else raw.toFloat()

    private fun readThermal(): ThermalState {
        val code = powerManager.currentThermalStatus
        val headroom = runCatching { powerManager.getThermalHeadroom(10) }.getOrNull()?.takeIf { !it.isNaN() }
        return ThermalState(
            statusCode = code,
            status = when (code) {
                PowerManager.THERMAL_STATUS_NONE -> "none"
                PowerManager.THERMAL_STATUS_LIGHT -> "light"
                PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "severe"
                PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
                else -> "unknown"
            },
            headroom = headroom,
        )
    }

    private fun readMemory(): MemoryState {
        val mi = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        return MemoryState(
            totalMb = mi.totalMem / (1024 * 1024),
            availableMb = mi.availMem / (1024 * 1024),
            lowMemory = mi.lowMemory,
            thresholdMb = mi.threshold / (1024 * 1024),
            appPssMb = Debug.getPss() / 1024,
        )
    }

    private fun readCpu(): CpuState = CpuState(
        cores = cpuCount,
        currentFreqMhz = (0 until cpuCount).map { readSysInt("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq") / 1000 },
        maxFreqMhz = maxFreqs,
        socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null,
        socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else null,
        abi = Build.SUPPORTED_ABIS.firstOrNull(),
    )

    private fun readSleep(): SleepState {
        val uptime = SystemClock.uptimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val sleptTotal = elapsed - uptime
        val tracker = SleepTracker.state.value
        val last = tracker.lastCompletedWindow
        val partial = SleepTracker.currentPartialWindow()
        val windowForVerdict = partial?.takeIf { it.durationMs > 30_000 } ?: last
        val verdict = when {
            windowForVerdict == null -> "Apaga la pantalla con el monitor activo para medir"
            windowForVerdict.durationMs < 60_000 -> "Ventana muy corta (<1 min); apaga la pantalla mas tiempo"
            windowForVerdict.sleepPercent >= 80f -> "Deep sleep correcto"
            windowForVerdict.sleepPercent >= 40f -> "Deep sleep parcial: algo despierta el SoC periodicamente"
            else -> "Sin deep sleep: wakelock, sync agresivo o radio activa"
        }
        return SleepState(
            uptimeMs = uptime,
            elapsedRealtimeMs = elapsed,
            deepSleepTotalMs = sleptTotal,
            deepSleepPercentSinceBoot = if (elapsed > 0) sleptTotal * 100f / elapsed else 0f,
            deepSleepPercentLastWindow = (partial ?: last)?.sleepPercent,
            lastWindowDurationMs = (partial ?: last)?.durationMs,
            screenOn = tracker.screenOn,
            interactive = powerManager.isInteractive,
            deviceIdleMode = powerManager.isDeviceIdleMode,
            lightIdleMode = if (Build.VERSION.SDK_INT >= 34) powerManager.isDeviceLightIdleMode else false,
            powerSaveMode = powerManager.isPowerSaveMode,
            ignoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName),
            verdict = verdict,
        )
    }

    private fun readSysInt(path: String): Int = runCatching {
        File(path).readText().trim().toInt()
    }.getOrDefault(0)
}
