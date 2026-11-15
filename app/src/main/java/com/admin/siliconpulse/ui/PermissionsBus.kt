package com.admin.siliconpulse.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cuando el usuario concede permisos en runtime, los repositorios que ya registraron
 * callbacks (ubicacion, bluetooth) necesitan re-suscribirse. Incrementar este contador
 * hace que los ViewModels reinicien su `flatMapLatest`.
 */
object PermissionsBus {
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation

    fun notifyChanged() {
        _generation.value = _generation.value + 1
    }
}
