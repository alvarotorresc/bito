package com.alvarotc.bito.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Hoja

/** La hoja del valor exacto de un hábito. Provisional: la rehace el carril c2. Aún no la llama nadie. */
@Composable
fun HojaTotalExacto(
    abierta: Boolean,
    card: HabitCardUi?,
    alGuardar: (Int) -> Unit,
    alCerrar: () -> Unit,
) {
    Hoja(abierta, alCerrar) {
        BotonesHoja(
            principal = stringResource(R.string.sistema_guardar),
            alPrincipal = { alGuardar(card?.progress ?: 0) },
        )
    }
}

/** La hoja de «Estuviste fuera». Provisional: la rehace el carril c2. Aún no la llama nadie. */
@Composable
fun HojaEstuvisteFuera(
    abierta: Boolean,
    dias: List<LogicalDay>,
    alSellar: () -> Unit,
    alCerrar: () -> Unit,
) {
    Hoja(abierta, alCerrar) {
        val texto =
            if (dias.size == 1) {
                stringResource(R.string.seal_sheet_confirm_one)
            } else {
                stringResource(R.string.seal_sheet_confirm_many, dias.size)
            }
        BotonesHoja(principal = texto, alPrincipal = alSellar)
    }
}
