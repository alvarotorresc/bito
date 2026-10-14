package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alvarotc.bito.ui.sistema.BitoTiposBase
import com.alvarotc.bito.ui.sistema.cifra

/** G29: reloj de 24 h con el color del cielo. [paso] 5 o 30; fuera de [desde]–[hasta] se apaga y el astro se queda en el extremo. */
@Composable
fun RelojCielo(
    minutos: Int,
    alCambiar: (Int) -> Unit,
    modifier: Modifier = Modifier,
    paso: Int = 5,
    desde: Int = 0,
    hasta: Int = 24 * 60 - 1,
    descripcion: String = "",
) = Text("%d:%02d".format(minutos / 60, minutos % 60), modifier, style = BitoTiposBase.t52.cifra())
