package com.alvarotc.bito.ui.camino

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas

/**
 * El contenido de Camino: las estadísticas en la tierra alta y debajo los logros. El jardín de arriba lo pone el
 * Inicio y sube al desplazar este scroll, así que aquí no hay jardín propio ni otro scroll vertical anidado.
 */
@Composable
fun CaminoContenido(
    estado: CaminoUiState,
    scroll: ScrollState,
    alVerEnJardin: (GardenSpecial?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var recordsAbierta by remember { mutableStateOf(false) }
    var numerosAbierta by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(scroll).background(BitoTema.colores.tAlta).testTag("inicio-camino")) {
        if (!estado.loading) {
            if (estado.sinHabitos) {
                Text(
                    stringResource(R.string.detail_empty_hint),
                    Modifier.fillMaxWidth().padding(horizontal = Medidas.margen, vertical = Medidas.e24).testTag("camino-vacio"),
                    style = BitoTema.tipos.t16,
                    color = BitoTema.colores.tintaSuave,
                )
            } else {
                EstadisticasCamino(estado, alRecords = { recordsAbierta = true }, alNumeros = { numerosAbierta = true })
            }
        }
        // Provisional hasta la Tarea 22: la cuenta de logros.
        val cuenta = stringResource(R.string.stats_badges_count, estado.conseguidos, estado.logros.size)
        Text(stringResource(R.string.stats_badges_title) + " · " + cuenta, Modifier.padding(Medidas.margen))
        HojaRecords(recordsAbierta, estado.records.records, alCerrar = { recordsAbierta = false })
        HojaNumeros(numerosAbierta, estado.numbers.totals, alCerrar = { numerosAbierta = false })
    }
}
