package com.alvarotc.bito.ui.premios

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.Movimiento
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.Saldo

/** La hoja del saldo. Provisional: la rehace el carril c3. */
@Composable
fun HojaSaldo(
    abierta: Boolean,
    saldo: Int,
    movimientos: List<Movimiento>,
    economia: EconomyConfig,
    alIrTienda: () -> Unit,
    alCerrar: () -> Unit,
) {
    Hoja(abierta, alCerrar, modifier = Modifier.testTag("hoja-saldo")) {
        Saldo(saldo)
        BotonesHoja(
            principal = stringResource(R.string.sistema_cerrar),
            alPrincipal = alCerrar,
            suave = stringResource(R.string.store_title),
            alSuave = alIrTienda,
        )
    }
}
