package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas

/** G32: escala a .97 (.94 si [redondo]) mientras se pulsa. Sin escala con reducido. */
fun Modifier.pulsar(
    interaccion: MutableInteractionSource,
    redondo: Boolean = false,
): Modifier =
    composed {
        val reducido = BitoTema.reducido
        val pulsado by interaccion.collectIsPressedAsState()
        val destino =
            when {
                reducido || !pulsado -> 1f
                redondo -> Duraciones.ESCALA_PULSAR_REDONDO
                else -> Duraciones.ESCALA_PULSAR
            }
        val escala by animateFloatAsState(destino, tween(Duraciones.PULSAR), label = "pulsar")
        graphicsLayer {
            scaleX = escala
            scaleY = escala
        }
    }

/** G37: temblor horizontal cada vez que cambia [clave] (no en la primera composición). [fuerte] = el de la passphrase. */
fun Modifier.temblor(
    clave: Any?,
    fuerte: Boolean = false,
): Modifier =
    composed {
        val reducido = BitoTema.reducido
        val densidad = LocalDensity.current
        val desplazamiento = remember { Animatable(0f) }
        val primera = remember { booleanArrayOf(true) }
        LaunchedEffect(clave) {
            if (primera[0]) {
                primera[0] = false
                return@LaunchedEffect
            }
            if (reducido) return@LaunchedEffect
            val pasos = if (fuerte) listOf(-6f, 5f, -3f, 2f, 0f) else listOf(-6f, 5f, -2f, 0f)
            val total = if (fuerte) Duraciones.TEMBLOR_FUERTE else Duraciones.TEMBLOR
            val tramo = total / pasos.size
            desplazamiento.snapTo(0f)
            desplazamiento.animateTo(
                0f,
                keyframes {
                    durationMillis = total
                    pasos.forEachIndexed { i, dp -> with(densidad) { dp.dp.toPx() } at tramo * (i + 1) }
                },
            )
        }
        graphicsLayer { translationX = desplazamiento.value }
    }

/** Franja de 6 dp color `costra` bajo el borde de abajo (catálogo §1.1). */
fun Modifier.costra(color: Color? = null): Modifier =
    composed {
        val franja = color ?: BitoTema.colores.costra
        drawBehind {
            val alto = Medidas.costra.toPx()
            drawRect(franja, topLeft = Offset(0f, size.height), size = Size(size.width, alto))
        }
    }
