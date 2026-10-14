package com.alvarotc.bito.ui.inicio

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalTransicionCompartida = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimacionRuta = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * El canto de un hábito que crece de su fila de Hoy a la cabecera de su ficha (pieza 3, §8.1).
 * Sin los dos locales (tests, Camino, archivados) o con «reducir animaciones», no hace nada.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.cantoCompartido(id: String): Modifier =
    composed {
        val compartida = LocalTransicionCompartida.current
        val ruta = LocalAnimacionRuta.current
        if (compartida == null || ruta == null || BitoTema.reducido) return@composed this
        with(compartida) {
            this@composed.sharedBounds(
                rememberSharedContentState("canto-$id"),
                ruta,
                // Spec §8.1: el canto crece de la fila a la cabecera en 440 ms con la curva del cajón. Va aquí para que
                // la fila (c2) y la cabecera de la ficha (c5) usen la misma sin tocar este archivo.
                boundsTransform = BoundsTransform { _, _ -> tween(440, easing = Curvas.drawer) },
            )
        }
    }
