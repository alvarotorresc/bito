package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

// (sin token) `#raiz` de tienda-habi.css.
private val ANCHO_RAIZ = 56.dp
private val ALTO_BOTON = 48.dp
private val NUDO = 40.dp
private val HILO_X = 27.dp
private val HILO_GROSOR = 2.dp
private val HILO_LARGO = 288.dp
private val HILO_ARRANQUE = 24.dp

/**
 * La raíz de la tira: siete nudos a la derecha unidos por un hilo; el de la ranura a la vista se llena de
 * agua. [paddingSuperior] lo calcula quien la coloca (`max(56, y - 4)`). Tocar un nudo suena `DESCENSO` con
 * `TICK` y llama a [alPulsar]; mover la tira es cosa de quien la usa. `testTag`: `raiz` y `raiz-<id>`.
 */
@Composable
fun RaizRanuras(
    activa: RanuraHabi,
    paddingSuperior: Dp,
    alPulsar: (RanuraHabi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val gesto = rememberGesto()
    val descripcion = stringResource(R.string.store_rail_desc)
    Box(modifier.testTag("raiz").width(ANCHO_RAIZ).fillMaxHeight().semantics { contentDescription = descripcion }) {
        Box(
            Modifier
                .offset(x = HILO_X, y = paddingSuperior + HILO_ARRANQUE)
                .size(HILO_GROSOR, HILO_LARGO)
                .testTag("raiz-hilo")
                .clip(CircleShape)
                .background(colores.raiz),
        )
        Column(Modifier.padding(top = paddingSuperior)) {
            RanuraHabi.entries.forEach { r ->
                NudoRaiz(r, r == activa) {
                    gesto(Snd.DESCENSO, Haptica.TICK)
                    alPulsar(r)
                }
            }
        }
    }
}

@Composable
private fun NudoRaiz(
    ranura: RanuraHabi,
    activa: Boolean,
    alTocar: () -> Unit,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val interaccion = remember { MutableInteractionSource() }
    val pulsado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (pulsado && !reducido) Duraciones.ESCALA_PULSAR_REDONDO else 1f,
        animationSpec = tween(Duraciones.PULSAR, easing = Curvas.out),
        label = "nudo-pulsar",
    )
    val relleno by animateColorAsState(if (activa) colores.agua else colores.tAlta, tween(Duraciones.COLOR), label = "nudo-relleno")
    val borde by animateColorAsState(if (activa) colores.contorno else colores.raiz, tween(Duraciones.COLOR), label = "nudo-borde")
    val nombre = stringResource(ranura.nombre)
    val descripcion = stringResource(R.string.store_rail_jump, nombre)
    Box(
        Modifier
            .testTag("raiz-${ranura.id}")
            .size(ANCHO_RAIZ, ALTO_BOTON)
            .clickable(interactionSource = interaccion, indication = null, role = Role.Button, onClick = alTocar)
            .semantics(mergeDescendants = true) {
                contentDescription = descripcion
                selected = activa
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .testTag("nudo-${ranura.id}")
                .size(NUDO)
                .graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                }.clip(CircleShape)
                .background(relleno)
                .border(BorderStroke(Medidas.linea, borde), CircleShape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            MiniaturaRiel(ranura)
        }
    }
}
