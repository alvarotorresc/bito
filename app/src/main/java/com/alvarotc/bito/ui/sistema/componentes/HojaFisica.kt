package com.alvarotc.bito.ui.sistema.componentes

import kotlin.math.abs
import kotlin.math.pow

/** Umbrales y fricción de la hoja (G7). */
object HojaFisica {
    /** Un tirón de más de esto (px/ms) cierra aunque sea corto. */
    const val VELOCIDAD_CIERRE = 0.11f

    /** Arrastrar más de esta fracción del alto cierra. */
    const val FRACCION_CIERRE = 0.30f

    fun debeCerrar(
        desplazamientoPx: Float,
        altoPx: Float,
        velocidadPxPorMs: Float,
    ): Boolean = velocidadPxPorMs > VELOCIDAD_CIERRE || desplazamientoPx > altoPx * FRACCION_CIERRE

    /** Hacia arriba (dy < 0) la hoja se resiste: |dy|^0,7. Hacia abajo, 1:1. */
    fun conFriccion(dy: Float): Float = if (dy >= 0f) dy else -abs(dy).pow(0.7f)
}
