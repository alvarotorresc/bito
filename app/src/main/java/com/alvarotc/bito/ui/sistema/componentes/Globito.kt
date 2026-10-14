package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas

/** Globito (G11): crece desde su cola y se va solo a los 2,4 s si [autoOcultar]. [cola] de 0 (izquierda) a 1 (derecha). */
@Composable
fun Globito(
    visible: Boolean,
    texto: String,
    modifier: Modifier = Modifier,
    alIrse: () -> Unit = {},
    autoOcultar: Boolean = true,
    cola: Float = 0.5f,
) {
    if (visible) Text(texto, modifier.background(BitoTema.colores.canto, BitoFormas.globo).padding(Medidas.e12))
}
