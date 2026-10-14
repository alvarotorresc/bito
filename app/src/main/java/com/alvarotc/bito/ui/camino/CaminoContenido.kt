package com.alvarotc.bito.ui.camino

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.ui.stats.StatsContenido

/** El contenido de Camino. Provisional: las estadísticas de siempre y el recuento de logros. Lo rehace el carril c4. */
@Composable
fun CaminoContenido(
    estado: CaminoUiState,
    scroll: ScrollState,
    alVerEnJardin: (GardenSpecial?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(scroll).testTag("inicio-camino")) {
        StatsContenido(estado.stats, {}, {}, {})
        Text("Logros · " + stringResource(R.string.stats_badges_count, estado.conseguidos, estado.logros.size))
    }
}
