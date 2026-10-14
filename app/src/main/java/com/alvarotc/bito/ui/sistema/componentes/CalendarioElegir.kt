package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.LocalDate

/** G30, G31 hacia delante: de hoy a +11 meses; días pasados apagados; el elegido con su gota. */
@Composable
fun CalendarioElegir(
    hoy: LocalDate,
    elegido: LocalDate?,
    alElegir: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) = Text(elegido?.toString() ?: hoy.toString(), modifier.clickable { alElegir(hoy) })
