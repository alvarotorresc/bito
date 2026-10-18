package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Suma a este relleno el hueco de las barras del sistema (la de estado arriba, la de navegación abajo) para el
 * contenido de una lista: se desplaza por debajo de las barras, pero arranca y acaba fuera de ellas.
 * El `Scaffold` exterior no consume insets, así que cada pantalla se aparta por su cuenta.
 */
@Composable
fun PaddingValues.masBarras(
    arriba: Boolean = true,
    abajo: Boolean = true,
): PaddingValues {
    val dir = LocalLayoutDirection.current
    val estado = WindowInsets.statusBars.asPaddingValues()
    val nav = WindowInsets.navigationBars.asPaddingValues()
    return PaddingValues.Absolute(
        left = calculateLeftPadding(dir),
        top = calculateTopPadding() + if (arriba) estado.calculateTopPadding() else 0.dp,
        right = calculateRightPadding(dir),
        bottom = calculateBottomPadding() + if (abajo) nav.calculateBottomPadding() else 0.dp,
    )
}
