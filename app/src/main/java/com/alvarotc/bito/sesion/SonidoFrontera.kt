package com.alvarotc.bito.sesion

import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd

/** Qué hace el fondo al cruzar una frontera. */
enum class AccionFondo { SONAR, CALLAR }

/**
 * Lo que suena y vibra al cruzar una frontera (spec §7), en un solo sitio: lo usan el motor (con la app delante) y el
 * receptor (con ella fuera), así que no pueden dar sonidos distintos. Al acabar la sesión ([nuevo] nulo) el motor suena
 * la campanilla; el receptor no, porque ahí avisa la notificación de fin.
 */
object SonidoFrontera {
    class Salida(
        val snd: Snd,
        val haptica: Haptica,
        val fondo: AccionFondo,
        val pararAire: Boolean,
    )

    fun de(
        previo: Tramo?,
        nuevo: Tramo?,
    ): Salida {
        val pararAire = previo is Tramo.Respirar
        return when (nuevo) {
            is Tramo.Foco ->
                if (previo is Tramo.Respirar) {
                    Salida(Snd.ALEJAR, Haptica.TICK, AccionFondo.SONAR, pararAire)
                } else {
                    Salida(Snd.BROTE, Haptica.RISE, AccionFondo.SONAR, pararAire)
                }
            is Tramo.Descanso -> Salida(Snd.CAMPANILLA, Haptica.CLICK, AccionFondo.CALLAR, pararAire)
            null -> Salida(Snd.CAMPANILLA, Haptica.CONFIRM, AccionFondo.CALLAR, pararAire)
            // Respirar solo abre la sesión: nunca se llega a él por una frontera.
            is Tramo.Respirar -> Salida(Snd.BROTE, Haptica.RISE, AccionFondo.CALLAR, pararAire)
        }
    }

    /**
     * Si tras la frontera hay sonido continuo que sostenga el servicio: un foco con fondo o, en un descanso, la espera
     * de volver a él. Sin fondo, o al acabar, el servicio se va.
     */
    fun servicioSigue(
        nuevo: Tramo?,
        fondo: SessionBackground,
    ): Boolean = nuevo != null && fondo != SessionBackground.NONE
}
