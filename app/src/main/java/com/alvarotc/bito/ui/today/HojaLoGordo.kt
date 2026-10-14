package com.alvarotc.bito.ui.today

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.habla.CandidatoGordo
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.Hoja

/**
 * «¿Qué es lo gordo de hoy?» (spec §7.2). Esqueleto de la Ola 0: cerrada no compone nada; abierta, la hoja con su
 * título y «Guardar», que entrega [elegidos] tal cual. Las filas, la sugerencia marcada y el tope de tres son de la
 * Tarea 24, que conserva esta firma.
 */
@Composable
fun HojaLoGordo(
    abierta: Boolean,
    candidatos: List<CandidatoGordo>,
    sugerencia: RefGordo?,
    elegidos: List<RefGordo>,
    alGuardar: (List<RefGordo>) -> Unit,
    alCerrar: () -> Unit,
) {
    Hoja(abierta, alCerrar, Modifier.testTag("hoja-gordo"), titulo = stringResource(R.string.habla_gordo_titulo)) {
        Boton(
            stringResource(R.string.habla_gordo_guardar),
            { alGuardar(elegidos) },
            Modifier.fillMaxWidth().padding(top = Medidas.e16).testTag("hoja-gordo-guardar"),
            estilo = EstiloBoton.PRINCIPAL,
        )
    }
}
