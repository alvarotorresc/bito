package com.alvarotc.bito.ui.settings.hojas

import androidx.compose.runtime.Composable
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.ui.sistema.componentes.Hoja

@Composable
fun HojaFondo(
    abierta: Boolean,
    elegido: SessionBackground,
    alElegir: (SessionBackground) -> Unit,
    alCerrar: () -> Unit,
) = Hoja(abierta = abierta, alCerrar = alCerrar) {}
