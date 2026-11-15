package com.admin.siliconpulse.data.power

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mide cuanto tiempo paso el SoC en suspend (deep sleep).
 *
 * Principio: `elapsedRealtime()` avanza siempre, `uptimeMillis()` se congela mientras
 * el CPU esta suspendido. La diferencia acumulada es el tiempo total en deep sleep.
 * Comparando dos lecturas (al apagar y al encender la pantalla) obtenemos el porcentaje
 * de deep sleep durante esa ventana. Si es bajo, algo (wakelock, alarma, radio) mantiene
 * el procesador despierto.
 */
object SleepTracker {

    data class Window(
        val startElapsed: Long,
        val startUptime: Long,
        val endElapsed: Long,
        val endUptime: Long,
    ) {
        val durationMs: Long get() = endElapsed - startElapsed
        val sleptMs: Long get() = durationMs - (endUptime - startUptime)
        val sleepPercent: Float get() = if (durationMs <= 0) 0f else (sleptMs * 100f / durationMs).coerceIn(0f, 100f)
    }

    data class Snapshot(
        val screenOn: Boolean = true,
        val currentWindowStartElapsed: Long? = null,
        val currentWindowStartUptime: Long? = null,
        val lastCompletedWindow: Window? = null,
        val history: List<Window> = emptyList(),
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    fun onScreenOff() {
        _state.value = _state.value.copy(
            screenOn = false,
            currentWindowStartElapsed = SystemClock.elapsedRealtime(),
            currentWindowStartUptime = SystemClock.uptimeMillis(),
        )
    }

    fun onScreenOn() {
        val s = _state.value
        val startE = s.currentWindowStartElapsed
        val startU = s.currentWindowStartUptime
        if (startE == null || startU == null) {
            _state.value = s.copy(screenOn = true)
            return
        }
        val window = Window(startE, startU, SystemClock.elapsedRealtime(), SystemClock.uptimeMillis())
        _state.value = s.copy(
            screenOn = true,
            currentWindowStartElapsed = null,
            currentWindowStartUptime = null,
            lastCompletedWindow = window,
            history = (s.history + window).takeLast(20),
        )
    }

    /** Ventana parcial mientras la pantalla sigue apagada (para la notificacion del servicio). */
    fun currentPartialWindow(): Window? {
        val s = _state.value
        val startE = s.currentWindowStartElapsed ?: return null
        val startU = s.currentWindowStartUptime ?: return null
        return Window(startE, startU, SystemClock.elapsedRealtime(), SystemClock.uptimeMillis())
    }
}
