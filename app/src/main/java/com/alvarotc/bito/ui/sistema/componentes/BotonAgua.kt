package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** «Hacer backup ahora»: se llena de agua de izquierda a derecha mientras [trabajando] (900 ms `drawer`). */
@Composable
fun BotonAgua(
    texto: String,
    trabajando: Boolean,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) = Boton(texto, alPulsar, modifier, apagado = trabajando)
