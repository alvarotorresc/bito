package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** Diálogo de confirmación (G10): brota desde abajo; el sí repite [verbo]; «Cancelar» como texto encima. */
@Composable
fun Dialogo(
    abierto: Boolean,
    titulo: String,
    verbo: String,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
    modifier: Modifier = Modifier,
    cuerpo: String? = null,
    peligro: Boolean = false,
) {
    if (!abierto) return
    AlertDialog(
        onDismissRequest = alCancelar,
        modifier = modifier,
        title = { Text(titulo) },
        text = cuerpo?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = alConfirmar) { Text(verbo) } },
        dismissButton = { TextButton(onClick = alCancelar) { Text(stringResource(android.R.string.cancel)) } },
    )
}
