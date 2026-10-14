package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** G26: la piedrecita corre 20 dp y la pista se llena de agua. Suena `interruptor(on)` y vibra `tick`. */
@Composable
fun Interruptor(
    activo: Boolean,
    alCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    descripcion: String? = null,
) {
    Switch(checked = activo, onCheckedChange = alCambiar, modifier = modifier, enabled = habilitado)
}
