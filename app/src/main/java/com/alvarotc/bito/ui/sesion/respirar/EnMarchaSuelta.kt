package com.alvarotc.bito.ui.sesion.respirar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.ui.sesion.FaseRespiracion
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.rememberGesto
import java.util.Locale

// `.ses-sub`: 6 dp arriba. `.btnrow`: 12 dp arriba.
private val ARRIBA_CICLO = 6.dp
private val ARRIBA_PARAR = 12.dp

private const val MARCA_RESTA = "\u0000"

/** «m:ss» de lo que queda, redondeado hacia arriba (`mmss`). */
internal fun mmss(segundos: Int): String = String.format(Locale.ROOT, "%d:%02d", segundos / 60, segundos % 60)

/**
 * Respirar suelta · en marcha (`data-b="srun"`): la fase sin puntitos (`s-fase`), «Ciclo {x} de {y}. Quedan {m:ss}»
 * centrado con el ciclo (`s-ciclo`) y lo que queda (`s-resta`) en nodos aparte, y «Parar» (`s-parar`).
 * [punto] solo cambia al cambiar de fase o de ciclo (el llenado no pasa por aquí) y [restaSegundos] cada segundo.
 */
@Composable
fun EnMarchaSuelta(
    punto: BreathingRhythm.Point,
    restaSegundos: Int,
    alParar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gesto = rememberGesto()
    val colores = BitoTema.colores
    val frase = stringResource(R.string.ses_ciclo_quedan, punto.cycle, punto.totalCycles, MARCA_RESTA)
    val antes = frase.substringBefore(MARCA_RESTA)
    val despues = frase.substringAfter(MARCA_RESTA, "")
    val resta = mmss(restaSegundos)
    Column(modifier.fillMaxWidth()) {
        FaseRespiracion(punto, punto.cycle, punto.totalCycles, mostrarPuntitos = false, modifier = Modifier.escalon(0).testTag("s-fase"))
        Row(
            Modifier.escalon(1).fillMaxWidth().padding(top = ARRIBA_CICLO).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(antes, style = BitoTema.tipos.t16, color = colores.tintaSuave, modifier = Modifier.testTag("s-ciclo"))
            Text(resta, style = BitoTema.tipos.t16, color = colores.tintaSuave, modifier = Modifier.testTag("s-resta"))
            if (despues.isNotEmpty()) Text(despues, style = BitoTema.tipos.t16, color = colores.tintaSuave)
        }
        Row(Modifier.escalon(2).fillMaxWidth().padding(top = ARRIBA_PARAR), horizontalArrangement = Arrangement.Center) {
            Boton(
                stringResource(R.string.breathing_stop),
                alPulsar = {
                    gesto(h = Haptica.LOW)
                    alParar()
                },
                modifier = Modifier.testTag("s-parar"),
                estilo = EstiloBoton.NORMAL,
            )
        }
    }
}
