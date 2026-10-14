package com.alvarotc.bito.ui.inicio

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.componentes.BotonTexto

/** La píldora Hoy / Camino. [q] va de 0 (Hoy) a 1 (Camino). Provisional: la rehace el carril c1. */
@Composable
fun Pildora(
    q: () -> Float,
    alHoy: () -> Unit,
    alCamino: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enHoy = q() < 0.5f
    Row(modifier.testTag("pildora")) {
        BotonTexto(
            stringResource(R.string.base_tab_hoy),
            alHoy,
            Modifier.testTag("pildora-hoy").semantics {
                role = Role.Tab
                selected = enHoy
            },
        )
        BotonTexto(
            stringResource(R.string.base_tab_camino),
            alCamino,
            Modifier.testTag("pildora-camino").semantics {
                role = Role.Tab
                selected = !enHoy
            },
        )
    }
}
