package com.admin.siliconpulse.domain.repository

import com.admin.siliconpulse.domain.model.PowerState
import com.admin.siliconpulse.domain.model.PrivacyState
import com.admin.siliconpulse.domain.model.RadioState
import com.admin.siliconpulse.domain.model.SamplingRate
import com.admin.siliconpulse.domain.model.SensorChannel
import com.admin.siliconpulse.domain.model.SensorDescriptor
import com.admin.siliconpulse.domain.model.SensorSample
import kotlinx.coroutines.flow.Flow

interface SensorRepository {
    /** Lista todos los sensores fisicos que reporta el HAL. */
    fun listSensors(): List<SensorDescriptor>

    /** Indica si un canal del osciloscopio tiene hardware disponible. */
    fun isAvailable(channel: SensorChannel): Boolean

    /**
     * Flujo frio de muestras crudas. Cada coleccion registra un listener y
     * lo elimina al cancelarse, por lo que no hay fugas entre pantallas.
     */
    fun samples(channel: SensorChannel, rate: SamplingRate): Flow<SensorSample>
}

interface PrivacyRepository {
    /** Estado en caliente de microfono, camara y ubicacion. */
    fun observe(): Flow<PrivacyState>
}

interface RadioRepository {
    fun observe(): Flow<RadioState>
}

interface PowerRepository {
    fun observe(): Flow<PowerState>
}
