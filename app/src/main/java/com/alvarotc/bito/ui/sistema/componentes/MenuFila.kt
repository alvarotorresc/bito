package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas

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
        Row(
            Modifier.fillMaxWidth().padding(top = Medidas.e12),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            opciones.forEach { o -> OpcionIcono(o, alPulsar = { cerrarY(o.alElegir) }) }
        }
    }
}

@Composable
fun OpcionIcono(
    opcion: OpcionMenu,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val relleno =
        when (opcion.tono) {
            TonoOpcion.AGUA -> colores.agua
            TonoOpcion.PIEDRA -> colores.piedraClara
            TonoOpcion.PELIGRO -> colores.peligroFondo
        }
    val tinta = if (opcion.tono == TonoOpcion.PELIGRO) colores.peligro else colores.tinta
    Column(
        modifier.defaultMinSize(minWidth = Medidas.toqueMinimo, minHeight = Medidas.toqueMinimo).clickable(onClick = alPulsar),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).background(relleno, CircleShape), contentAlignment = Alignment.Center) {
            Icon(opcion.icono, contentDescription = null, tint = tinta)
        }
        Text(
            opcion.texto,
            style = BitoTema.tipos.t14,
            color = colores.tinta,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Medidas.e4),
        )
    }
}
