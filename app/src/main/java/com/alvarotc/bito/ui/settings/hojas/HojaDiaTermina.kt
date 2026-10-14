package com.alvarotc.bito.ui.settings.hojas

import androidx.compose.runtime.Composable
import com.alvarotc.bito.ui.sistema.componentes.Hoja

@Composable
fun HojaDiaTermina(
    abierta: Boolean,
    minutos: Int,
    alGuardar: (Int) -> Unit,
    alCerrar: () -> Unit,
) = Hoja(abierta = abierta, alCerrar = alCerrar) {}
