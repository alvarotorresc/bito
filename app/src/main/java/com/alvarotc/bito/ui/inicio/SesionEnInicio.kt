package com.alvarotc.bito.ui.inicio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.ui.sesion.TramoVisto

/**
 * La región viva educada de la sesión (spec §17): anuncia los cambios de tramo («Bloque 2 de 4», «Descanso de 5
 * minutos», «Sesión terminada»), no la cifra cada segundo. No se ve ni se toca.
 */
@Composable
internal fun AnuncioTramo(
    tramo: TramoVisto?,
    registro: RegistroSesion?,
) {
    val recursos = LocalContext.current.resources
    val texto =
        remember(registro?.id, tramo) {
            val plan = registro?.plan
            when (val actual = tramo?.actual) {
                is Tramo.Foco -> plan?.let { recursos.getString(R.string.ses_bloque_n_de_m, actual.bloque, it.bloques) }
                is Tramo.Descanso -> recursos.getQuantityString(R.plurals.sesr_anuncio_descanso, actual.minutos, actual.minutos)
                is Tramo.Respirar -> null
                null -> if (tramo != null) recursos.getString(R.string.sesr_anuncio_terminada) else null
            }
        }
    // Siempre montada: un nodo vivo que aparece de golpe puede no anunciarse; solo cambia su texto (vacío en Respirar).
    Box(
        Modifier
            .size(1.dp)
            .testTag("ses-anuncio")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = texto.orEmpty()
            },
    )
}

/** Cuando ya se pintó el cambio de tramo de la vuelta (dos fotogramas tras recuperar), el cambio siguiente es una frontera viva. */
internal suspend fun quitarAdopcion(contador: IntArray) {
    withFrameNanos { }
    withFrameNanos { }
    contador[0] = (contador[0] - 1).coerceAtLeast(0)
}
