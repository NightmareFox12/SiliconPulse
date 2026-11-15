package com.admin.siliconpulse.ui.oscilloscope

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.admin.siliconpulse.domain.model.SamplingRate
import com.admin.siliconpulse.domain.model.SensorChannel
import com.admin.siliconpulse.domain.model.SensorDescriptor
import com.admin.siliconpulse.domain.repository.SensorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OscilloscopeUiState(
    val channel: SensorChannel = SensorChannel.ACCELEROMETER,
    val rate: SamplingRate = SamplingRate.GAME,
    val paused: Boolean = false,
    val available: Map<SensorChannel, Boolean> = emptyMap(),
    val sensors: List<SensorDescriptor> = emptyList(),
    val error: String? = null,
    val sampleRateHz: Float = 0f,
    val accuracy: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class OscilloscopeViewModel(private val repo: SensorRepository) : ViewModel() {

    val buffer = TraceBuffer(capacity = 512)

    /** Contador de frames: la UI lo observa para redibujar como maximo a ~60 fps, sin importar la tasa del sensor. */
    var frame by mutableIntStateOf(0)
        private set

    private val _ui = MutableStateFlow(
        OscilloscopeUiState(
            available = SensorChannel.entries.associateWith { repo.isAvailable(it) },
            sensors = repo.listSensors(),
        )
    )
    val ui: StateFlow<OscilloscopeUiState> = _ui.asStateFlow()

    private var frameJob: Job? = null

    init {
        viewModelScope.launch {
            combine(_ui.map3()) { it }
                .flatMapLatest { (channel, rate, paused) ->
                    buffer.clear()
                    if (paused) kotlinx.coroutines.flow.emptyFlow()
                    else repo.samples(channel, rate).catch { e -> _ui.update { s -> s.copy(error = e.message) } }
                }
                .collectLatest { sample ->
                    buffer.push(sample.x, sample.y, sample.z, sample.timestampNanos)
                    if (sample.accuracy != _ui.value.accuracy) _ui.update { it.copy(accuracy = sample.accuracy) }
                }
        }
        frameJob = viewModelScope.launch {
            while (isActive) {
                delay(16)
                frame++
                if (frame % 30 == 0) _ui.update { it.copy(sampleRateHz = buffer.estimatedHz()) }
            }
        }
    }

    fun select(channel: SensorChannel) = _ui.update { it.copy(channel = channel, error = null) }
    fun setRate(rate: SamplingRate) = _ui.update { it.copy(rate = rate) }
    fun togglePause() = _ui.update { it.copy(paused = !it.paused) }

    private fun MutableStateFlow<OscilloscopeUiState>.map3() =
        kotlinx.coroutines.flow.flow {
            collect { emit(Triple(it.channel, it.rate, it.paused)) }
        }.let { kotlinx.coroutines.flow.distinctUntilChanged(it) }
}

private fun <T> kotlinx.coroutines.flow.FlowCollector<T>.unused() = Unit
