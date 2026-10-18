package com.alvarotc.bito.ui.tienda.habi

import com.alvarotc.bito.domain.model.EquippedSet

/**
 * El salto de Habi al comprar (`SALTO_MAX` y `saltoCompra` de tienda-habi.js). La amplitud es una fracción del alto
 * del cuerpo, como la mide el mock; [enLienzo] la pasa a la del lienzo, que es la que usa el salto de la app.
 */
internal object SaltoCompra {
    /** Lo más que sube Habi, en fracción del cuerpo (`min(0,24, SALTO_MAX)`). */
    const val TOPE = 0.24f

    private const val SIN_NADA = 0.22f
    private const val OTRA_CABEZA = 0.11f

    // Alto del cuerpo y del lienzo en el rig: el mock mide el salto sobre el primero, la app sobre el segundo.
    private const val ALTO_CUERPO = 1404f
    private const val ALTO_LIENZO = 1786f

    /** Lo que sube cada pieza que tapa o carga la cabeza, por id del catálogo. */
    private val SALTO_MAX =
        mapOf(
            "head-gorro-lana" to 0.15f,
            "head-boina" to 0.11f,
            "head-paja" to 0.11f,
            "head-auriculares" to 0.07f,
            "head-copa" to 0.05f,
            "head-flor" to 0.20f,
            "head-corona" to 0.06f,
            "costume-calabaza" to 0.13f,
            "costume-reno" to 0.03f,
            "costume-dinosaurio" to 0.14f,
        )

    /** Sube lo que deja la pieza de más arriba: el disfraz si lo hay; si no, la cabeza; si no, [SIN_NADA]. Reducido: no salta. */
    fun amplitud(
        look: EquippedSet,
        reducido: Boolean = false,
    ): Float {
        if (reducido) return 0f
        val clave = look.costume ?: look.head ?: return limitar(SIN_NADA)
        return limitar(SALTO_MAX[clave] ?: OTRA_CABEZA)
    }

    fun limitar(amplitud: Float): Float = minOf(TOPE, amplitud)

    /** El mismo salto medido sobre el lienzo de Habi. */
    fun enLienzo(amplitud: Float): Float = amplitud * ALTO_CUERPO / ALTO_LIENZO
}
