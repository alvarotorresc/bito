package com.alvarotc.bito.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.alvarotc.bito.domain.PlantaFicha
import com.alvarotc.bito.domain.WindowStats
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.today.CardKind

/** La lámina de herbario de la ficha. Provisional: solo la racha actual. La rehace el carril c5. */
@Composable
fun Lamina(
    planta: PlantaFicha,
    mejor: Int,
    pct30: WindowStats,
    actual: Int,
    periodo: Period,
    kind: CardKind,
    modifier: Modifier = Modifier,
) {
    Box(modifier.testTag("lamina")) { Text(actual.toString()) }
}
