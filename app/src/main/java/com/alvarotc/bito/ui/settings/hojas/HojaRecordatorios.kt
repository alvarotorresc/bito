package com.alvarotc.bito.ui.settings.hojas

import androidx.compose.runtime.Composable
import com.alvarotc.bito.ui.sistema.componentes.Hoja

@Composable
fun HojaRecordatorios(
    abierta: Boolean,
    horas: List<Int>,
    alAnadir: (Int) -> Unit,
    alCambiar: (antes: Int, despues: Int) -> Unit,
    alQuitar: (Int) -> Unit,
    alCerrar: () -> Unit,
) = Hoja(abierta = abierta, alCerrar = alCerrar) {}
