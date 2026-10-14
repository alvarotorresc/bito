package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alvarotc.bito.ui.sistema.Estrato
import com.alvarotc.bito.ui.sistema.LocalEstrato

/** G36: se abre y se cierra con su caret. */
@Composable
fun Plegable(
    titulo: String,
    abierto: Boolean,
    alCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        TextButton(onClick = { alCambiar(!abierto) }) { Text(titulo) }
        if (abierto) contenido()
    }
}

/** Un estrato de Ajustes (3.5): cabecera con título y [resumen] (cerrado), cuerpo con `capa`. Provee `LocalEstrato`. */
@Composable
fun EstratoPlegable(
    estrato: Estrato,
    titulo: String,
    resumen: String,
    abierto: Boolean,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalEstrato provides estrato) {
        Column(modifier.fillMaxWidth().background(estrato.fondo)) {
            TextButton(onClick = alPulsar) { Text(if (abierto) titulo else "$titulo · $resumen", color = estrato.tinta) }
            if (abierto) contenido()
        }
    }
}
