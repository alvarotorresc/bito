package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.alvarotc.bito.ui.sistema.BitoTiposBase
import com.alvarotc.bito.ui.sistema.cifra

/** G27: ± y la cifra rueda hacia donde va. En el tope el botón queda al 35 %, no responde y tiembla. */
@Composable
fun Stepper(
    valor: Int,
    alCambiar: (Int) -> Unit,
    min: Int,
    max: Int,
    descripcion: String,
    modifier: Modifier = Modifier,
    paso: Int = 1,
    formato: (Int) -> String = { it.toString() },
) {
    Row(modifier) {
        TextButton(onClick = { if (valor - paso >= min) alCambiar(valor - paso) }) { Text("-") }
        Text(formato(valor))
        TextButton(onClick = { if (valor + paso <= max) alCambiar(valor + paso) }) { Text("+") }
    }
}

/** La cifra que rueda (G27): entra desde abajo si sube, desde arriba si baja. */
@Composable
fun CifraRodillo(
    valor: Int,
    modifier: Modifier = Modifier,
    estilo: TextStyle = BitoTiposBase.t24.cifra(),
    color: Color = Color.Unspecified,
    formato: (Int) -> String = { it.toString() },
) {
    Text(formato(valor), modifier, style = estilo, color = color)
}
