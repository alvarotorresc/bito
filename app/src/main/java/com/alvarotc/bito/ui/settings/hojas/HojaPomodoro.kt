package com.alvarotc.bito.ui.settings.hojas

import androidx.compose.runtime.Composable
import com.alvarotc.bito.ui.sistema.componentes.Hoja

data class Pomodoro(
    val foco: Int,
    val corto: Int,
    val largo: Int,
    val rondas: Int,
    val descansoSolo: Boolean,
)

@Composable
fun HojaPomodoro(
    abierta: Boolean,
    valores: Pomodoro,
    alCambiar: (Pomodoro) -> Unit,
    alCerrar: () -> Unit,
) = Hoja(abierta = abierta, alCerrar = alCerrar) {}
