package com.alvarotc.bito.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.RelojCielo

private val LADO_RELOJ = 260.dp
private const val PASO_RELOJ = 5

/**
 * «Otra hora» (spec §7.1): el reloj del cielo acotado a [desde]..[hasta] (minutos del día) y «Guardar», que entrega el
 * minuto elegido. [actual] fuera de la ventana abre en el extremo más cercano.
 */
@Composable
fun HojaOtraHora(
    abierta: Boolean,
    desde: Int,
    hasta: Int,
    actual: Int,
    alGuardar: (Int) -> Unit,
    alCerrar: () -> Unit,
) {
    val minimo = minOf(desde, hasta)
    val maximo = maxOf(desde, hasta)
    var minuto by remember(abierta, actual, minimo, maximo) { mutableIntStateOf(actual.coerceIn(minimo, maximo)) }
    Hoja(abierta, alCerrar, Modifier.testTag("hoja-hora"), titulo = stringResource(R.string.habla_otra_hora)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            RelojCielo(
                minuto,
                { minuto = it },
                Modifier.size(LADO_RELOJ).testTag("hoja-hora-reloj"),
                paso = PASO_RELOJ,
                desde = minimo,
                hasta = maximo,
                descripcion = stringResource(R.string.habla_otra_hora),
            )
        }
        Boton(
            stringResource(R.string.save),
            { alGuardar(minuto) },
            Modifier.fillMaxWidth().padding(top = Medidas.e16).testTag("hoja-hora-guardar"),
            estilo = EstiloBoton.PRINCIPAL,
        )
    }
}
