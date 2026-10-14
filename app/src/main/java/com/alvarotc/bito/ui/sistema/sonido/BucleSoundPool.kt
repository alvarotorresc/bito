package com.alvarotc.bito.ui.sistema.sonido

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bucle sobre un [Reproductor]. Todo el estado se toca solo desde el hilo del [scope] (Main.immediate en producción).
 * Entra con rampa de 400 ms y sale con rampa de 600 ms (spec §4.4.1).
 */
class BucleSoundPool(
    private val reproductor: Reproductor,
    private val id: () -> Int,
    private val objetivo: () -> Float,
    private val scope: CoroutineScope,
) : Bucle {
    private var stream = 0
    private var rampa: Job? = null
    private var actual = 0f

    @Volatile
    override var sonando: Boolean = false
        private set

    override fun empezar() {
        if (sonando) return
        val sample = id()
        if (!reproductor.listo(sample)) return
        // Una salida en rampa aún pendiente deja su stream sonando: se corta antes de abrir otro.
        rampa?.cancel()
        if (stream != 0) reproductor.parar(stream)
        sonando = true
        actual = 0f
        stream = reproductor.play(sample, 0f, bucle = true)
        correr(objetivo(), 400, pararAlFinal = false)
    }

    override fun parar() {
        if (!sonando) return
        sonando = false
        correr(0f, 600, pararAlFinal = true)
    }

    /** Segundo plano: rampa corta y para. */
    fun callar() {
        if (!sonando) return
        sonando = false
        correr(0f, 150, pararAlFinal = true)
    }

    private fun correr(
        hasta: Float,
        ms: Int,
        pararAlFinal: Boolean,
    ) {
        rampa?.cancel()
        val s = stream
        val desde = actual
        rampa =
            scope.launch {
                for (v in Rampa.pasos(desde, hasta, ms)) {
                    actual = v
                    reproductor.volumen(s, v)
                    delay(16)
                }
                if (pararAlFinal) reproductor.parar(s)
            }
    }
}
