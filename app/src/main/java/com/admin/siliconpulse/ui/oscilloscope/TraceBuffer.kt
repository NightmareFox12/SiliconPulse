package com.admin.siliconpulse.ui.oscilloscope

/**
 * Buffer circular de 3 canales sin asignaciones en caliente.
 * Un unico escritor (la corrutina del ViewModel) y lectores en el hilo de dibujo.
 * Las lecturas pueden ver un frame parcialmente escrito; para una grafica es aceptable.
 */
class TraceBuffer(val capacity: Int) {
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val z = FloatArray(capacity)
    private val t = LongArray(capacity)

    @Volatile var head: Int = 0
        private set

    @Volatile var size: Int = 0
        private set

    @Volatile var lastX = 0f; private set
    @Volatile var lastY = 0f; private set
    @Volatile var lastZ = 0f; private set

    fun push(vx: Float, vy: Float, vz: Float, timestampNanos: Long) {
        x[head] = vx; y[head] = vy; z[head] = vz; t[head] = timestampNanos
        head = (head + 1) % capacity
        if (size < capacity) size++
        lastX = vx; lastY = vy; lastZ = vz
    }

    fun clear() {
        head = 0; size = 0; lastX = 0f; lastY = 0f; lastZ = 0f
    }

    /** Indice fisico del i-esimo elemento mas antiguo (0 = mas viejo). */
    private fun physical(i: Int): Int = (head - size + i + capacity * 2) % capacity

    fun xAt(i: Int) = x[physical(i)]
    fun yAt(i: Int) = y[physical(i)]
    fun zAt(i: Int) = z[physical(i)]
    fun tAt(i: Int) = t[physical(i)]

    /** Frecuencia de muestreo estimada sobre el contenido actual. */
    fun estimatedHz(): Float {
        if (size < 2) return 0f
        val span = tAt(size - 1) - tAt(0)
        return if (span <= 0) 0f else (size - 1) * 1_000_000_000f / span
    }

    /** min/max sobre los canales activos, con margen para que la traza no toque los bordes. */
    fun range(axes: Int): Pair<Float, Float> {
        if (size == 0) return -1f to 1f
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (i in 0 until size) {
            val a = xAt(i); if (a < min) min = a; if (a > max) max = a
            if (axes > 1) { val b = yAt(i); if (b < min) min = b; if (b > max) max = b }
            if (axes > 2) { val c = zAt(i); if (c < min) min = c; if (c > max) max = c }
        }
        if (max - min < 1e-3f) { min -= 0.5f; max += 0.5f }
        val pad = (max - min) * 0.1f
        return (min - pad) to (max + pad)
    }
}
