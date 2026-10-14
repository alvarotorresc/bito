package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class FamiliaLogro { RACHAS, CONSTANCIA, MOMENTOS }

/** Emblema de logro (dirección C, G13): anillo de [progreso] 0–1; [celebrar] lanza su momento de encenderse. Lo usa la pieza 3. */
@Composable
fun Emblema(
    familia: FamiliaLogro,
    progreso: Float,
    encendido: Boolean,
    modifier: Modifier = Modifier,
    tamano: Dp = 120.dp,
    celebrar: Boolean = false,
    arte: @Composable () -> Unit = {},
) = Box(modifier.size(tamano)) { arte() }
