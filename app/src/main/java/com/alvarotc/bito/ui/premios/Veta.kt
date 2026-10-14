package com.alvarotc.bito.ui.premios

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** La veta de una celebración. Provisional: la rehace el carril c3. */
@Composable
fun Veta(
    tipo: TipoVeta,
    titulo: String,
    frase: String,
    n: Int,
    arte: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.testTag("veta-$tipo")) {
        arte()
        Text(titulo)
        Text(frase)
        Text("+$n")
    }
}
