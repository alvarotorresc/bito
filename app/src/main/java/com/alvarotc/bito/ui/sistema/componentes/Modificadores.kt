package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.alvarotc.bito.ui.sistema.fuerte

/** G32: escala a .97 (.94 si [redondo]) mientras se pulsa. Sin escala con reducido. */
fun Modifier.pulsar(
    interaccion: MutableInteractionSource,
    redondo: Boolean = false,
): Modifier = this

/** G37: temblor horizontal cada vez que cambia [clave] (no en la primera composición). [fuerte] = el de la passphrase. */
fun Modifier.temblor(
    clave: Any?,
    fuerte: Boolean = false,
): Modifier = this

/** Franja de 6 dp color `costra` bajo el borde de abajo (catálogo §1.1). */
fun Modifier.costra(color: Color? = null): Modifier = this
