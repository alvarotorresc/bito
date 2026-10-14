package com.alvarotc.bito.ui.inicio

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.ui.garden.GardenScene
import com.alvarotc.bito.ui.habi.HabiSpec

/** El jardín del Inicio, que encoge al pasar a Camino. Provisional: lo rehace el carril c1. */
@Composable
fun JardinCompartido(
    garden: GardenState,
    spec: HabiSpec,
    q: () -> Float,
    encogido: () -> Float,
    modifier: Modifier = Modifier,
    contento: Boolean = false,
    animado: Boolean = true,
    alTocarHabi: () -> Unit = {},
    alTocarFondo: () -> Unit = {},
    llegando: GardenSpecial? = null,
    creciendo: Int? = null,
    destacar: GardenSpecial? = null,
    saltoHabi: Int = 0,
) {
    GardenScene(
        garden,
        spec,
        modifier.fillMaxWidth(),
        delighted = contento,
        onHabiTap = alTocarHabi,
        animated = animado,
        alTocarFondo = alTocarFondo,
        llegando = llegando,
        creciendo = creciendo,
        destacar = destacar,
        saltoHabi = saltoHabi,
    )
}
