package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.alvarotc.bito.domain.model.Personality

/** 3.4, D14: Habi grande y sin accesorios en su hornacina con la cara de la voz; tres filas de texto. */
@Composable
fun HojaPersonalidad(
    abierta: Boolean,
    actual: Personality,
    alElegir: (Personality) -> Unit,
    alCerrar: () -> Unit,
) {
    Hoja(abierta = abierta, alCerrar = alCerrar) {
        Personality.entries.forEach { p -> TextButton(onClick = { alElegir(p) }) { Text(p.name) } }
    }
}
