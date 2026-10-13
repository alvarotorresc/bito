package com.alvarotc.bito.ui.theme

import androidx.compose.runtime.Composable
import com.alvarotc.bito.ui.sistema.BitoSistema

/** Bito tiene un solo tema (D2, modo claro). Es el sistema de la Bito 2. */
@Composable
fun BitoTheme(content: @Composable () -> Unit) = BitoSistema(content = content)
