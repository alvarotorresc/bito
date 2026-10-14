package com.alvarotc.bito.ui.theme

import androidx.compose.runtime.Composable
import com.alvarotc.bito.ui.sistema.BitoSistema
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.TactoMudo
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import com.alvarotc.bito.ui.sistema.sonido.MotorSonidoMudo

/** Bito tiene un solo tema (D2, modo claro). Es el sistema de la Bito 2. */
@Composable
fun BitoTheme(
    reducirAnimaciones: Boolean = false,
    sonido: MotorSonido = MotorSonidoMudo,
    tacto: Tacto = TactoMudo,
    content: @Composable () -> Unit,
) = BitoSistema(reducirAnimaciones, sonido, tacto, content)
