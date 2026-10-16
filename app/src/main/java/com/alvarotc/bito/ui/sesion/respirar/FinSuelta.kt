package com.alvarotc.bito.ui.sesion.respirar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.BreathingStats
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.fuerte

// `.tarjeta-habi`: relleno 14 / 16, «Habi» a 2 dp del texto, texto 16 sp / 500 con alto de línea 1,4.
private val RELLENO_VERTICAL_TARJETA = 14.dp
private val ENTRE_QUIEN_Y_TEXTO = 2.dp
private val ALTO_LINEA_TARJETA = 1.4.em
private val ARRIBA_SEMANA = 12.dp
private val ARRIBA_BOTONES = 12.dp

/**
 * Respirar suelta · fin (`data-b="sfin"`): la tarjeta de Habi con [frase] (`sf-txt`), lo de esta semana y lo de todo
 * el tiempo, y «Otra vez» (normal) y «Listo» (principal).
 */
@Composable
fun FinSuelta(
    frase: String,
    semana: BreathingStats.Tally?,
    total: BreathingStats.Tally?,
    alOtraVez: () -> Unit,
    alListo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .escalon(0)
                .fillMaxWidth()
                .testTag("tarjeta-habi")
                .background(colores.canto, BitoFormas.tarjeta)
                .border(Medidas.linea, colores.contorno, BitoFormas.tarjeta)
                .padding(horizontal = Medidas.e16, vertical = RELLENO_VERTICAL_TARJETA),
        ) {
            Text(stringResource(R.string.hoy_habi), style = BitoTema.tipos.t14.fuerte(), color = colores.tintaSuave)
            Text(
                frase,
                style = BitoTema.tipos.t16.copy(lineHeight = ALTO_LINEA_TARJETA),
                color = colores.tinta,
                modifier = Modifier.padding(top = ENTRE_QUIEN_Y_TEXTO).testTag("sf-txt"),
            )
        }
        if (semana != null) {
            Text(
                textoSemana(semana),
                style = BitoTema.tipos.t16.cifras(),
                color = colores.tintaSuave,
                textAlign = TextAlign.Center,
                modifier = Modifier.escalon(1).padding(top = ARRIBA_SEMANA).fillMaxWidth().testTag("sf-semana"),
            )
        }
        if (total != null) {
            Text(
                stringResource(
                    R.string.breathing_all_time,
                    pluralStringResource(R.plurals.breathing_sessions, total.sessions, total.sessions),
                    total.minutes,
                ),
                style = BitoTema.tipos.t16.cifras(),
                color = colores.tintaSuave,
                textAlign = TextAlign.Center,
                modifier = Modifier.escalon(2).fillMaxWidth().testTag("sf-total"),
            )
        }
        Row(
            Modifier.escalon(3).fillMaxWidth().padding(top = ARRIBA_BOTONES),
            horizontalArrangement = Arrangement.spacedBy(Medidas.e8),
        ) {
            Boton(
                stringResource(R.string.breathing_again),
                alOtraVez,
                Modifier.weight(1f).testTag("sf-otra"),
                estilo = EstiloBoton.NORMAL,
                alto = Medidas.botonHoja,
            )
            Boton(
                stringResource(R.string.ses_listo),
                alListo,
                Modifier.weight(1f).testTag("sf-listo"),
                estilo = EstiloBoton.PRINCIPAL,
                alto = Medidas.botonHoja,
            )
        }
    }
}
