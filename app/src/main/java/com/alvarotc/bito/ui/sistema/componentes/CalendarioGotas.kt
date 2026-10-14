package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.alvarotc.bito.domain.HeatmapDay
import com.alvarotc.bito.domain.model.LogicalDay
import java.time.YearMonth

/** El calendario de gotas de la ficha. Provisional: solo el mes. Lo rehace el carril c5. */
@Composable
fun CalendarioGotas(
    mes: YearMonth,
    dias: List<HeatmapDay>,
    primerMes: YearMonth,
    ultimoMes: YearMonth,
    alMesAnterior: () -> Unit,
    alMesSiguiente: () -> Unit,
    alTocarDia: (LogicalDay) -> Unit,
    modifier: Modifier = Modifier,
    interactivo: Boolean = true,
) {
    Text(mes.toString(), modifier.testTag("calendario-gotas"))
}
