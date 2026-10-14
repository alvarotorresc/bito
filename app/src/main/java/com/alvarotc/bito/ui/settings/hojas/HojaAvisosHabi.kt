package com.alvarotc.bito.ui.settings.hojas

import androidx.compose.runtime.Composable
import com.alvarotc.bito.ui.sistema.componentes.Hoja

@Composable
fun HojaAvisosHabi(
    abierta: Boolean,
    manana: Int,
    noche: Int,
    alGuardarManana: (Int) -> Unit,
    alGuardarNoche: (Int) -> Unit,
    alCerrar: () -> Unit,
) = Hoja(abierta = abierta, alCerrar = alCerrar) {}
