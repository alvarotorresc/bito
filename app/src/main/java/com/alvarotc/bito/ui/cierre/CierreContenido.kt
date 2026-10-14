package com.alvarotc.bito.ui.cierre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import com.alvarotc.bito.ui.premios.CelebracionesViewModel
import com.alvarotc.bito.ui.review.ReviewScreen
import com.alvarotc.bito.ui.theme.Papel

/**
 * Lo que se ve con el cierre abierto, encima del Inicio. Esqueleto de la Ola 0: el repaso de siempre a pantalla
 * completa, con su fondo y sin envolverlo en otro scroll (el repaso ya tiene su lista). Se funde con [estado]`.c`.
 *
 * - [alCerrar]: ✕ o atrás sin sellar; el Inicio baja la cámara a Hoy.
 * - [alBuenasNoches]: el cierre terminó con el día sellado («Hasta mañana»); el Inicio vuelve a Hoy, ya de noche.
 * - [celebraciones]: la cola de las celebraciones, para que el cierre la retenga y la suelte (ruling P23).
 */
@Composable
fun CierreContenido(
    estado: EstadoCierre,
    viewModel: CierreViewModel,
    alCerrar: () -> Unit,
    alBuenasNoches: () -> Unit,
    modifier: Modifier = Modifier,
    celebraciones: CelebracionesViewModel? = null,
) {
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = estado.c.value.coerceIn(0f, 1f) }
            .background(Papel)
            .testTag("cierre"),
    ) {
        ReviewScreen(viewModel.repaso, onClose = alCerrar)
    }
}
