package com.admin.siliconpulse.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Interruptor global del monitoreo en vivo.
 *
 * `active` es verdadero solo cuando la app esta en primer plano Y el usuario no la ha pausado.
 * Todos los flujos de telemetria pasan por [gate], de modo que al salir de la app o pulsar
 * "Pausar" se desregistran sensores, callbacks de red, ubicacion y bucles de dibujo.
 * El unico trabajo en segundo plano permitido es el servicio opcional de deep sleep.
 */
object MonitoringController {
    private val _inForeground = MutableStateFlow(false)
    private val _userPaused = MutableStateFlow(false)

    val userPaused: StateFlow<Boolean> = _userPaused
    val inForeground: StateFlow<Boolean> = _inForeground

    val active: Flow<Boolean> = combine(_inForeground, _userPaused) { fg, paused -> fg && !paused }

    fun setForeground(value: Boolean) { _inForeground.value = value }
    fun setPaused(value: Boolean) { _userPaused.value = value }
    fun togglePaused() { _userPaused.value = !_userPaused.value }

    /** Envuelve un flujo para que solo exista mientras el monitoreo esta activo. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> gate(source: () -> Flow<T>): Flow<T> = active.flatMapLatest { if (it) source() else emptyFlow() }
}
