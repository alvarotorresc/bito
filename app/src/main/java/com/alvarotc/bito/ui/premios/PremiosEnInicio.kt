package com.alvarotc.bito.ui.premios

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alvarotc.bito.domain.model.GardenSpecial

/**
 * Las celebraciones del Inicio. Provisional: no pinta nada y no llama a `terminado`, así que si la noche se para
 * las celebraciones esperan en la cola en vez de perderse. La rehace el carril c3.
 */
@Composable
fun PremiosEnInicio(
    celebraciones: CelebracionesViewModel,
    registro: RegistroAnclas,
    costuraY: () -> Float,
    alVerEnJardin: (GardenSpecial?) -> Unit,
    alJardin: (llegando: GardenSpecial?, creciendo: Int?) -> Unit,
    alAsomarCabecera: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
}
