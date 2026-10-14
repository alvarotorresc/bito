package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Fila de ajustes o de lista (GUIA `.arow`). Toma tinta y línea de `LocalEstrato` si la hay. */
@Composable
fun Fila(
    etiqueta: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    alPulsar: (() -> Unit)? = null,
    habilitada: Boolean = true,
    derecha: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().then(if (alPulsar != null && habilitada) Modifier.clickable(onClick = alPulsar) else Modifier)) {
        Column(Modifier.weight(1f)) {
            Text(etiqueta)
            subtitulo?.let { Text(it) }
        }
        derecha?.invoke()
    }
}

@Composable
fun FilaInterruptor(
    etiqueta: String,
    activo: Boolean,
    alCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    habilitada: Boolean = true,
) = Fila(etiqueta, modifier, subtitulo, alPulsar = { alCambiar(!activo) }, habilitada = habilitada) {
    Interruptor(activo, alCambiar, habilitado = habilitada)
}

@Composable
fun FilaValor(
    etiqueta: String,
    valor: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    habilitada: Boolean = true,
) = Fila(etiqueta, modifier, subtitulo, alPulsar, habilitada) { Text(valor) }

/** [externo]: abre fuera de la app (lleva la flecha de enlace externo). */
@Composable
fun FilaEnlace(
    etiqueta: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    externo: Boolean = false,
) = Fila(etiqueta, modifier, subtitulo, alPulsar)
