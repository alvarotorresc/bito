package com.alvarotc.bito.ui.inicio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import kotlin.math.roundToInt

/** Ancho y alto de la píldora (catálogo 3.2, movimiento.md 1b.3). */
internal val ANCHO_PILDORA = 236.dp
internal val ALTO_PILDORA = 56.dp
private val AIRE_PILDORA = 4.dp
private val RADIO_PILDORA = 28.dp

/**
 * La píldora Hoy / Camino, flotante: 236 por 56 dp. [q] va de 0 (Hoy) a 1 (Camino) y mueve la cápsula de
 * tierra honda con el mismo progreso que el jardín. Cada pestaña es un `Tab` con su estado.
 */
@Composable
fun Pildora(
    q: () -> Float,
    alHoy: () -> Unit,
    alCamino: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val enHoy by remember { derivedStateOf { q() < 0.5f } }
    val tab = (ANCHO_PILDORA - AIRE_PILDORA * 2) / 2
    val descripcion = stringResource(R.string.inicio_pildora_desc)
    Box(
        modifier
            .testTag("pildora")
            .semantics { contentDescription = descripcion }
            .size(ANCHO_PILDORA, ALTO_PILDORA)
            .shadow(12.dp, BitoFormas.canto, ambientColor = colores.sombraFlota, spotColor = colores.sombraFlota)
            .background(colores.canto, BitoFormas.canto)
            .border(BorderStroke(Medidas.linea, colores.contorno), BitoFormas.canto),
    ) {
        // La cápsula de tierra honda viaja con q, a la altura de las pestañas.
        Box(
            Modifier
                .padding(AIRE_PILDORA)
                .offset { IntOffset((q().coerceIn(0f, 1f) * tab.toPx()).roundToInt(), 0) }
                .width(tab)
                .fillMaxHeight()
                .background(colores.tHonda, BitoFormas.canto),
        )
        Row(Modifier.padding(AIRE_PILDORA).height(ALTO_PILDORA - AIRE_PILDORA * 2), horizontalArrangement = Arrangement.Start) {
            Pestana(
                stringResource(R.string.base_tab_hoy),
                IconosSistema.Sun,
                IconosSistema.SunFill,
                activa = enHoy,
                alPulsar = alHoy,
                modifier = Modifier.width(tab).testTag("pildora-hoy"),
            )
            Pestana(
                stringResource(R.string.base_tab_camino),
                IconosSistema.Plant,
                IconosSistema.PlantFill,
                activa = !enHoy,
                alPulsar = alCamino,
                modifier = Modifier.width(tab).testTag("pildora-camino"),
            )
        }
    }
}

@Composable
private fun Pestana(
    texto: String,
    icono: ImageVector,
    iconoRelleno: ImageVector,
    activa: Boolean,
    alPulsar: () -> Unit,
    modifier: Modifier,
) {
    val colores = BitoTema.colores
    val tinta = if (activa) colores.tintaClara else colores.tinta
    Row(
        modifier
            .fillMaxHeight()
            .semantics {
                role = Role.Tab
                selected = activa
            }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = alPulsar),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(if (activa) iconoRelleno else icono, contentDescription = null, tint = tinta, modifier = Modifier.size(22.dp))
        Text(
            texto,
            style = BitoTema.tipos.t16.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = tinta,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
