package com.alvarotc.bito.ui.sistema.sonido

/** Rampas de volumen por pasos de 16 ms (spec §4.4.1): nunca a saltos. */
object Rampa {
    fun pasos(
        desde: Float,
        hasta: Float,
        ms: Int,
        pasoMs: Int = 16,
    ): List<Float> {
        val n = maxOf(1, (ms + pasoMs - 1) / pasoMs)
        return (1..n).map { i -> desde + (hasta - desde) * i / n }
    }
}
