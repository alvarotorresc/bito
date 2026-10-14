package com.alvarotc.bito.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Las acciones del pie de la ficha. */
data class AccionesPie(
    val pausar: (nota: String?) -> Unit,
    val reanudar: () -> Unit,
    val archivar: () -> Unit,
    val reactivar: () -> Unit,
    val recaer: () -> Unit,
    val irTienda: () -> Unit,
)

/** El pie de la ficha. Provisional: vacío. Lo rehace el carril c5. */
@Composable
fun PieFicha(
    estado: DetailUiState,
    acciones: AccionesPie,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {}
}
