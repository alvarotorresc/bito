package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** G25: el indicador corre y cambia de ancho. Suena `tic`, vibra `seg`. */
@Composable
fun Segmentado(
    opciones: List<String>,
    elegida: Int,
    alElegir: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier) { opciones.forEachIndexed { i, o -> TextButton(onClick = { alElegir(i) }) { Text(o) } } }
}
