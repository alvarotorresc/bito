package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role

enum class FormaAgua { FILA, CANTO }

data class OpcionAgua(
    val clave: String,
    val texto: String,
    val subtitulo: String? = null,
    val icono: ImageVector? = null,
)

/** G24: el aro de lo elegido se llena de agua y el anterior se vacía. Nunca una fila de chips. */
@Composable
fun ElegirConAgua(
    opciones: List<OpcionAgua>,
    elegida: String?,
    alElegir: (String) -> Unit,
    modifier: Modifier = Modifier,
    forma: FormaAgua = FormaAgua.FILA,
) {
    Column(modifier.selectableGroup()) {
        opciones.forEach { o ->
            Row(Modifier.selectable(selected = o.clave == elegida, onClick = { alElegir(o.clave) }, role = Role.RadioButton)) {
                Text(o.texto)
            }
        }
    }
}
