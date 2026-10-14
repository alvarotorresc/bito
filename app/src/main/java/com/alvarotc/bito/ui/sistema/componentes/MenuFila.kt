package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class TonoOpcion { AGUA, PIEDRA, PELIGRO }

data class OpcionMenu(
    val texto: String,
    val icono: ImageVector,
    val tono: TonoOpcion,
    val alElegir: () -> Unit,
)

/** Menú de fila (GUIA `menuFila`): una hoja con opciones de icono en círculo. La acción llega 160 ms después de empezar a cerrar. */
@Composable
fun MenuFila(
    abierto: Boolean,
    alCerrar: () -> Unit,
    opciones: List<OpcionMenu>,
    modifier: Modifier = Modifier,
    titulo: String? = null,
) {
    Hoja(abierta = abierto, alCerrar = alCerrar, modifier = modifier, titulo = titulo) {
        Row { opciones.forEach { o -> OpcionIcono(o, alPulsar = { cerrarY(o.alElegir) }) } }
    }
}

@Composable
fun OpcionIcono(
    opcion: OpcionMenu,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(onClick = alPulsar)) {
        Icon(opcion.icono, contentDescription = null)
        Text(opcion.texto)
    }
}
