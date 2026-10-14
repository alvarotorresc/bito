package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** La monedita de oro con la carita de Habi (D15, §7.1). 18, 36 o 48 dp. */
@Composable
fun Monedita(
    tamano: Dp,
    modifier: Modifier = Modifier,
) = Box(modifier.size(tamano))

/** Saldo de bitos: monedita y cifra en rodillo; tic a .94 cuando cambia. */
@Composable
fun Saldo(
    bitos: Int,
    modifier: Modifier = Modifier,
) = Row(modifier) { CifraRodillo(bitos) }
