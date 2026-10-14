package com.alvarotc.bito.ui.sistema.componentes

import kotlin.math.abs
import kotlin.math.pow

/** Umbrales y fricción de la hoja (G7). */
object HojaFisica {
    /** Un tirón de más de esto (dp/ms: los px del catálogo son px CSS = dp, no píxeles de pantalla) cierra aunque sea corto. */
    const val VELOCIDAD_CIERRE = 0.11f

    /** Arrastrar más de esta fracción del alto cierra. */
    const val FRACCION_CIERRE = 0.30f

    /** Velocidad del dedo de px/s de pantalla a dp/ms. */
    fun dpPorMs(
        pxPorSegundo: Float,
        densidad: Float,
    ): Float = pxPorSegundo / densidad / 1000f

    fun debeCerrar(
        desplazamientoPx: Float,
        altoPx: Float,
        velocidadDpPorMs: Float,
    ): Boolean = velocidadDpPorMs > VELOCIDAD_CIERRE || desplazamientoPx > altoPx * FRACCION_CIERRE

    /** Hacia arriba (dy < 0) la hoja se resiste: |dy|^0,7. Hacia abajo, 1:1. */
    fun conFriccion(dy: Float): Float = if (dy >= 0f) dy else -abs(dy).pow(0.7f)
}
