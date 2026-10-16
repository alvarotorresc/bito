package com.alvarotc.bito.ui.cierre

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.habla.BolsasRecursos
import com.alvarotc.bito.ui.inicio.textoHabi
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.pulsar
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlin.math.abs

/** Cuánto sube la respuesta de Habi al brotar (catálogo 3.8). */
private val BROTE_RESPUESTA = 6.dp

private const val BROTE_RESPUESTA_MS = 240

private val bolsasAnimo = listOf(Bolsa.CIE_ANIMO_BIEN, Bolsa.CIE_ANIMO_REGULAR, Bolsa.CIE_ANIMO_DURO)

/**
 * «¿Qué tal el día?»: tres píldoras en un grupo de selección única (Bien, Regular, Duro). La elegida se llena de
 * agua; la respuesta de Habi brota 6 dp. El ánimo es opcional y se puede cambiar hasta las buenas noches.
 * [dia] elige qué frase de la bolsa toca (la misma del mismo día).
 */
@Composable
internal fun AnimoCierre(
    elegido: Int?,
    voz: Personality,
    dia: LogicalDay,
    alElegir: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    val etiquetas =
        listOf(
            stringResource(R.string.cierre_animo_bien),
            stringResource(R.string.cierre_animo_regular),
            stringResource(R.string.cierre_animo_duro),
        )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
        Text(stringResource(R.string.cierre_animo_titulo), style = BitoTema.tipos.t16.fuerte(), color = colores.tinta)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            etiquetas.forEachIndexed { valor, etiqueta ->
                val elegida = elegido == valor
                val interaccion = remember { MutableInteractionSource() }
                val relleno by animateColorAsState(
                    if (elegida) colores.agua else colores.canto,
                    tween(Duraciones.COLOR),
                    label = "animoRelleno",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = Medidas.toqueMinimo)
                        .testTag("cierre-animo-$valor")
                        .selectable(
                            selected = elegida,
                            interactionSource = interaccion,
                            indication = null,
                            role = Role.RadioButton,
                            onClick = {
                                gesto(Snd.TIC, Haptica.SEG)
                                alElegir(valor)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .pulsar(interaccion)
                            .fillMaxWidth()
                            .heightIn(min = Medidas.boton)
                            .clip(BitoFormas.canto)
                            .background(relleno)
                            .border(Medidas.linea, colores.contorno, BitoFormas.canto),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(etiqueta, style = BitoTema.tipos.t16.fuerte(), color = colores.tinta)
                    }
                }
            }
        }
        RespuestaAnimo(elegido, voz, dia, reducido)
    }
}

@Composable
private fun RespuestaAnimo(
    elegido: Int?,
    voz: Personality,
    dia: LogicalDay,
    reducido: Boolean,
) {
    val brote = remember { Animatable(1f) }
    LaunchedEffect(elegido) {
        if (elegido != null) {
            brote.snapTo(0f)
            brote.animateTo(1f, tween(if (reducido) Duraciones.REDUCIDO else BROTE_RESPUESTA_MS, easing = Curvas.out))
        }
    }
    if (elegido == null) return
    val bolsa = bolsasAnimo[elegido]
    val frases = stringArrayResource(BolsasRecursos.array(bolsa, voz)).size
    val indice = if (frases > 0) abs(dia) % frases else 0
    Text(
        textoHabi(TextoHabi.Frase(listOf(ClaveTrozo(bolsa, voz, indice)), emptyMap())),
        style = BitoTema.tipos.t16,
        color = BitoTema.colores.tinta,
        modifier =
            Modifier
                .testTag("cierre-animo-respuesta")
                .padding(top = Medidas.e4)
                .graphicsLayer {
                    alpha = brote.value
                    if (!reducido) translationY = (1f - brote.value) * BROTE_RESPUESTA.toPx()
                },
    )
}
