package com.alvarotc.bito.ui.sesion.respirar

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.BreathingStats
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.FilaInterruptor
import com.alvarotc.bito.ui.sistema.componentes.Segmentado
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto

// `.seg` bajo el título: 12 dp. `.ses-ritmo`: 10 dp arriba, alto de línea 1,4 em. `.ses-filas`: 8 dp. `.btnrow`: 12 dp.
private val ARRIBA_SEGMENTADO = 12.dp
private val ARRIBA_RITMO = 10.dp
private val ARRIBA_FILAS = 8.dp
private val ARRIBA_BOTONES = 12.dp
private val ARRIBA_CUENTA = 6.dp
private val ALTO_LINEA_RITMO = 1.4.em

internal val MODOS = BreathingMode.entries

@StringRes
internal fun nombreDeModo(m: BreathingMode): Int =
    when (m) {
        BreathingMode.CALM -> R.string.ses_modo_calm
        BreathingMode.SLEEP -> R.string.ses_modo_sleep
        BreathingMode.FOCUS -> R.string.ses_modo_focus
    }

@StringRes
private fun ritmoDeModo(m: BreathingMode): Int =
    when (m) {
        BreathingMode.CALM -> R.string.breathing_rhythm_calm
        BreathingMode.SLEEP -> R.string.breathing_rhythm_sleep
        BreathingMode.FOCUS -> R.string.breathing_rhythm_focus
    }

/** «Esta semana: {n} sesión/sesiones · {m} min» (`semanaTxt()`). */
@Composable
internal fun textoSemana(t: BreathingStats.Tally): String =
    stringResource(
        R.string.breathing_week,
        androidx.compose.ui.res.pluralStringResource(R.plurals.breathing_sessions, t.sessions, t.sessions),
        t.minutes,
    )

/**
 * Respirar suelta · elegir (`data-b="suelta"`): «Respirar», los tres modos, el ritmo con rodillo, la música, «Empezar» y
 * lo de esta semana. [semana] es `null` hasta que se sabe de verdad.
 */
@Composable
fun ElegirSuelta(
    modo: BreathingMode,
    alModo: (BreathingMode) -> Unit,
    musica: Boolean,
    alMusica: (Boolean) -> Unit,
    semana: BreathingStats.Tally?,
    alEmpezar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gesto = rememberGesto()
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.breathing_title),
            style = BitoTema.tipos.t19.fuerte(),
            color = BitoTema.colores.tinta,
        )
        Segmentado(
            opciones = MODOS.map { stringResource(nombreDeModo(it)) },
            elegida = MODOS.indexOf(modo),
            alElegir = { alModo(MODOS[it]) },
            modifier = Modifier.padding(top = ARRIBA_SEGMENTADO).fillMaxWidth().testTag("r-modo"),
        )
        TextoRodillo(
            stringResource(ritmoDeModo(modo)),
            Modifier.padding(top = ARRIBA_RITMO).fillMaxWidth().testTag("r-ritmo"),
        )
        FilaInterruptor(
            etiqueta = stringResource(R.string.ses_musica_fondo),
            subtitulo = stringResource(R.string.ses_musica_fondo_desc),
            activo = musica,
            alCambiar = alMusica,
            modifier = Modifier.padding(top = ARRIBA_FILAS).testTag("r-mus"),
        )
        Boton(
            stringResource(R.string.breathing_start),
            alPulsar = {
                gesto(h = Haptica.CONFIRM)
                alEmpezar()
            },
            modifier = Modifier.padding(top = ARRIBA_BOTONES).fillMaxWidth().testTag("r-empezar"),
            estilo = EstiloBoton.PRINCIPAL,
            alto = Medidas.botonHoja,
        )
        if (semana != null) {
            Text(
                textoSemana(semana),
                style = BitoTema.tipos.t16,
                color = BitoTema.colores.tintaSuave,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = ARRIBA_CUENTA).fillMaxWidth().testTag("r-semana"),
            )
        }
    }
}

/** El ritmo que rueda (`B.rodillo(..., 1)`): el texto nuevo entra desde abajo. Reducido: fundido. */
@Composable
private fun TextoRodillo(
    texto: String,
    modifier: Modifier,
) {
    val reducido = BitoTema.reducido
    AnimatedContent(
        targetState = texto,
        modifier = modifier,
        transitionSpec = {
            if (reducido) {
                ContentTransform(fadeIn(tween(Duraciones.REDUCIDO)), fadeOut(tween(Duraciones.REDUCIDO)))
            } else {
                val medida = tween<Float>(Duraciones.RODILLO, easing = Curvas.out)
                val posicion = tween<androidx.compose.ui.unit.IntOffset>(Duraciones.RODILLO, easing = Curvas.out)
                (slideInVertically(posicion) { it } + fadeIn(medida)) togetherWith (slideOutVertically(posicion) { -it } + fadeOut(medida))
            }
        },
        label = "ritmoRodillo",
    ) { t ->
        Text(t, style = BitoTema.tipos.t16.copy(lineHeight = ALTO_LINEA_RITMO), color = BitoTema.colores.tintaSuave)
    }
}
