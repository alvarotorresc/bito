package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

private val ANCHO_PISTA = 52.dp
private val ALTO_PISTA = 32.dp
private val PIEDRECITA = 24.dp
private val HOLGURA = 4.dp
private val RECORRIDO = 20.dp

/** G26: la piedrecita corre 20 dp y la pista se llena de agua. Suena `interruptor(on)` y vibra `tick`. */
@Composable
fun Interruptor(
    activo: Boolean,
    alCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    descripcion: String? = null,
) {
    val gesto = rememberGesto()
    Box(
        modifier
            .size(maxOf(ANCHO_PISTA, Medidas.toqueMinimo), maxOf(ALTO_PISTA, Medidas.toqueMinimo))
            .alpha(if (habilitado) 1f else 0.35f)
            .semantics { if (descripcion != null) contentDescription = descripcion }
            .toggleable(
                value = activo,
                enabled = habilitado,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                gesto(if (it) Snd.INTERRUPTOR_ON else Snd.INTERRUPTOR_OFF, Haptica.TICK)
                alCambiar(it)
            },
        contentAlignment = Alignment.Center,
    ) {
        PistaInterruptor(activo)
    }
}

/** Solo el dibujo del interruptor, sin gesto ni semántica: lo usa la fila, que es la que se pulsa. */
@Composable
internal fun PistaInterruptor(
    activo: Boolean,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val pista by animateColorAsState(
        if (activo) colores.agua else colores.tAlta2,
        tween(Duraciones.COLOR),
        label = "pista",
    )
    val corrida: Dp by animateDpAsState(if (activo) RECORRIDO else 0.dp, Muelles.interruptor(reducido), label = "piedrecita")
    Box(
        modifier
            .size(ANCHO_PISTA, ALTO_PISTA)
            .clip(BitoFormas.canto)
            .background(pista)
            .border(BorderStroke(Medidas.linea, colores.contorno), BitoFormas.canto),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = HOLGURA + corrida)
                .size(PIEDRECITA)
                .clip(BitoFormas.canto)
                .background(colores.canto)
                .border(BorderStroke(Medidas.linea, colores.contorno), BitoFormas.canto),
        )
    }
}
