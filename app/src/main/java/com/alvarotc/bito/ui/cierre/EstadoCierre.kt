package com.alvarotc.bito.ui.cierre

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * El cierre del día dentro del Inicio (spec §7.4, §7.5). [c] lleva la cámara: 0 es Hoy y 1 el cierre; [noche]
 * lleva el anochecer del jardín: 0 es de día y 1 de noche (G40). El Inicio los lee para hundir la lista, subir el
 * jardín y pintar la noche; el cierre los mueve sin tocar el Inicio.
 */
@Stable
class EstadoCierre(
    val c: Animatable<Float, AnimationVector1D>,
    val noche: Animatable<Float, AnimationVector1D>,
) {
    /** Abierto o abriéndose: lo que manda es adónde va la cámara, no dónde está. */
    val abierto: Boolean get() = c.targetValue > 0f

    /**
     * La noche que pinta el jardín. Con el cierre abierto, o con el anochecer aún en marcha, sigue a [noche] aunque
     * el día se selle a mitad (el sello llega a 1,3 s de 2,4 s); fuera del cierre, de noche solo con el día sellado.
     */
    fun nocheDelJardin(diaSellado: Boolean): Float =
        when {
            abierto || noche.isRunning -> noche.value
            diaSellado -> 1f
            else -> 0f
        }

    /** Al abrir: el jardín arranca en la noche del día que hay. Un anochecer en marcha no se pisa. */
    suspend fun ajustarNoche(diaSellado: Boolean) {
        if (!noche.isRunning) noche.snapTo(if (diaSellado) 1f else 0f)
    }

    companion object {
        /** Sobrevive a girar la pantalla: el cierre abierto sigue abierto y de noche si lo estaba. */
        internal val Guardar: Saver<EstadoCierre, List<Float>> =
            Saver(
                save = { listOf(it.c.targetValue, it.noche.targetValue) },
                restore = { (c, noche) -> EstadoCierre(Animatable(c), Animatable(noche)) },
            )
    }
}

/**
 * [abiertoAlEmpezar] abre el cierre sin animar (el enlace `review`, la ruta con `cierre = true`); con
 * [selladoAlEmpezar] además arranca de noche, para que el jardín no vuelva al día.
 */
@Composable
fun rememberEstadoCierre(
    abiertoAlEmpezar: Boolean = false,
    selladoAlEmpezar: Boolean = false,
): EstadoCierre =
    rememberSaveable(saver = EstadoCierre.Guardar) {
        val abierto = if (abiertoAlEmpezar) 1f else 0f
        EstadoCierre(Animatable(abierto), Animatable(if (abiertoAlEmpezar && selladoAlEmpezar) 1f else 0f))
    }
