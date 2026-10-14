package com.alvarotc.bito.ui.camino

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.stats.BadgeStrings

enum class ModoLogro { NUEVO, CONSULTA }

/** La hoja de un logro. Provisional: la rehace el carril c4. */
@Composable
fun HojaLogro(
    abierta: Boolean,
    logro: LogroUi?,
    modo: ModoLogro,
    alVerEnJardin: () -> Unit,
    alCerrar: () -> Unit,
) {
    Hoja(abierta, alCerrar) {
        if (logro != null) Text(stringResource(BadgeStrings.badgeNameRes(logro.def.id)), Modifier.testTag("hoja-logro"))
    }
}
