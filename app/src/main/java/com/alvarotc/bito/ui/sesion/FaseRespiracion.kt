package com.alvarotc.bito.ui.sesion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.domain.model.BreathPhase
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas

// `.ses-fase`: alto de línea 1,25 em.
private val ALTO_LINEA_FASE = 1.25.em

// `.dots i`: puntos de 9 dp con 5 dp de hueco; `.ses-ciclos`: 12 dp arriba.
private val PUNTITO = 9.dp
private val HUECO_PUNTITOS = 5.dp
private val ARRIBA_PUNTITOS = 12.dp

/** Cuántos puntitos encendidos hay, para las pruebas (los puntitos son decorativos para el lector). */
internal val PuntitosEncendidos = SemanticsPropertyKey<Int>("PuntitosEncendidos")

/**
 * Los puntitos encendidos (`paso()` del mock): los ciclos hechos y el actual cuando exhala; todos al terminar.
 * [ciclo] empieza en 1.
 */
internal fun puntitosEncendidos(
    punto: BreathingRhythm.Point?,
    ciclo: Int,
    ciclos: Int,
): Int {
    if (punto?.finished == true) return ciclos
    val hechos = ciclo - 1
    val actual = if (punto?.phase == BreathPhase.EXHALE) 1 else 0
    return (hechos + actual).coerceIn(0, ciclos)
}

/**
 * La fase de respirar (`.ses-fase`): «Inhala», «Mantén» o «Exhala» con el rodillo (Inhala y Mantén entran desde
 * abajo, Exhala desde arriba) y, con [mostrarPuntitos], los [ciclos] puntitos debajo. El lector anuncia la fase
 * al cambiar, nada más. [modifier] va a la palabra: su `testTag` es `e-fase` salvo que quien llama ponga el suyo
 * (la respiración suelta pasa `s-fase`). Los puntitos llevan `e-dots`.
 */
@Composable
fun FaseRespiracion(
    punto: BreathingRhythm.Point?,
    ciclo: Int,
    ciclos: Int,
    mostrarPuntitos: Boolean,
    modifier: Modifier = Modifier,
) {
    val fase = punto?.phase ?: BreathPhase.INHALE
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PalabraFase(fase, modifier.testTag("e-fase"))
        if (mostrarPuntitos) {
            Spacer(Modifier.height(ARRIBA_PUNTITOS))
            Puntitos(puntitosEncendidos(punto, ciclo, ciclos), ciclos, Modifier.testTag("e-dots"))
        }
    }
}

@Composable
private fun PalabraFase(
    fase: BreathPhase,
    modifier: Modifier,
) {
    val texto =
        stringResource(
            when (fase) {
                BreathPhase.INHALE -> R.string.breathing_phase_inhale
                BreathPhase.HOLD -> R.string.breathing_phase_hold
                BreathPhase.EXHALE -> R.string.breathing_phase_exhale
            },
        )
    val reducido = BitoTema.reducido
    // El rodillo (`B.rodillo`): la palabra nueva entra desde un alto de línea con opacidad 0. La primera no se mueve.
    val entrada = remember { Animatable(1f) }
    val primera = remember { booleanArrayOf(true) }
    LaunchedEffect(fase) {
        if (primera[0]) {
            primera[0] = false
            return@LaunchedEffect
        }
        entrada.snapTo(0f)
        entrada.animateTo(
            1f,
            if (reducido) tween(Duraciones.REDUCIDO, easing = Curvas.lin) else tween(Duraciones.RODILLO, easing = Curvas.out),
        )
    }
    val direccion = if (fase == BreathPhase.EXHALE) -1f else 1f
    Box(Modifier.fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
        Text(
            texto,
            modifier =
                modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .graphicsLayer {
                        val p = entrada.value
                        alpha = p
                        translationY = if (reducido) 0f else direccion * (1f - p) * size.height
                    },
            style = BitoTema.tipos.t32.copy(fontWeight = FontWeight.Bold, lineHeight = ALTO_LINEA_FASE),
            color = BitoTema.colores.tinta,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Los puntitos (`.dots`): 9 dp con 5 dp de hueco; la respiración y los bloques de Foco los comparten. */
@Composable
internal fun Puntitos(
    encendidos: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    Row(
        modifier.semantics { this[PuntitosEncendidos] = encendidos },
        horizontalArrangement = Arrangement.spacedBy(HUECO_PUNTITOS),
    ) {
        repeat(total) { i ->
            val fondo by animateColorAsState(
                if (i < encendidos) colores.agua else Color.Transparent,
                tween(Duraciones.COLOR),
                label = "puntito",
            )
            Box(
                Modifier
                    .size(PUNTITO)
                    .background(fondo, CircleShape)
                    .border(Medidas.linea, colores.contorno, CircleShape),
            )
        }
    }
}
