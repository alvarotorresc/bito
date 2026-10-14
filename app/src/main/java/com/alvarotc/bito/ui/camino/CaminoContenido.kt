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
    // El logro se queda aunque la hoja ya se cierre: su salida todavía lo pinta.
    var logroAbierto by remember { mutableStateOf<LogroUi?>(null) }
    var logroVisible by remember { mutableStateOf(false) }
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
        LogrosCamino(estado, alTocar = { logro ->
            logroAbierto = logro
            logroVisible = true
        })
        HojaRecords(recordsAbierta, estado.records.records, alCerrar = { recordsAbierta = false })
        HojaNumeros(numerosAbierta, estado.numbers.totals, alCerrar = { numerosAbierta = false })
        HojaLogro(
            abierta = logroVisible,
            logro = logroAbierto,
            modo = ModoLogro.CONSULTA,
            alVerEnJardin = { alVerEnJardin(logroAbierto?.especial) },
            alCerrar = { logroVisible = false },
            hoy = estado.hoy,
            mesImposible = mesImposible(estado),
        )
    }
}

/**
 * Mes redondo ya no puede salir este mes: algún día anterior de hoy no fue perfecto. Los días perfectos de este
 * mes llegan hasta hoy, así que con menos que los días ya pasados del mes alguno falló.
 */
internal fun mesImposible(estado: CaminoUiState): Boolean =
    !estado.sinHabitos && estado.stats.perfectDays.thisMonth < estado.hoy.dayOfMonth - 1
