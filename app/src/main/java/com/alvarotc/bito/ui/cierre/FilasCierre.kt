@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.alvarotc.bito.ui.cierre

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonTexto
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.pulsar
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.HabitCardUi
import com.alvarotc.bito.ui.today.llenado
import kotlinx.coroutines.launch
import kotlin.math.max

/** Cuánto atenúa una fila decidida sin hacer (G: el color cambia en 200 ms). */
private const val ATENUADA = 0.45f

/** El agua de «Día limpio» baja a esto y vuelve a subir (catálogo 3.8, G1). */
private const val AGUA_DIA_LIMPIO = 0.62f

private const val AMOUNT_CORTO = 5
private const val AMOUNT_LARGO = 15

/**
 * Una fila del cierre del día (spec §7.4): el hábito con lo que se puede hacer ahora mismo según su tipo.
 * Marcar suma ([alHecho], [alSumar], [alFijar]) y la fila se queda con la cuenta nueva; decidir sin hacer
 * ([alDecidir]: «No lo hice», «Así se queda», «Día limpio», «Dentro del límite») no escribe nada: atenúa la fila y
 * deja [alDeshacer]. «He recaído» solo avisa ([alRecaer]): el diálogo es del contenedor. Sonido y háptica de cada
 * gesto suenan aquí, en el mismo fotograma que el cambio.
 */
@Composable
internal fun FilaCierre(
    card: HabitCardUi,
    decidida: Boolean,
    alHecho: () -> Unit,
    alSumar: (Int) -> Unit,
    alFijar: (Int) -> Unit,
    alExacto: () -> Unit,
    alDecidir: () -> Unit,
    alRecaer: () -> Unit,
    alDeshacer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val t = BitoTema.tipos
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    val scope = rememberCoroutineScope()
    val limite = card.direction == Direction.AT_MOST
    val dejar = card.kind == CardKind.ABSTINENCE || card.direction == Direction.ZERO
    val suma = card.kind == CardKind.COUNTER || card.kind == CardKind.DURATION

    // El agua sigue al hábito; «Día limpio» la baja al 62 % y la devuelve (el gesto se ve, no solo se oye).
    val meta = llenado(card)
    val nivel = remember { Animatable(meta) }
    LaunchedEffect(meta) { nivel.animateTo(meta, if (reducido) tween(Duraciones.REDUCIDO) else Muelles.marcar()) }
    val atenuacion by animateFloatAsState(if (decidida) ATENUADA else 1f, tween(Duraciones.COLOR), label = "cierreAtenuada")

    fun sumar(cantidad: Int) {
        val llega = !limite && card.progress < card.target && card.progress + cantidad >= card.target
        val pasa = limite && !card.failed && card.progress + cantidad > card.target
        when {
            llega -> gesto(Snd.GORGOTEO, Haptica.TICKS)
            pasa -> gesto(Snd.VACIAR, Haptica.LOW)
            else -> gesto(Snd.TIC, Haptica.TICK)
        }
        alSumar(cantidad)
    }

    fun decidirSinHacer() {
        gesto(Snd.HUNDIR, Haptica.LOW)
        alDecidir()
    }

    fun exacto() {
        gesto(Snd.BROTE, Haptica.CLICK)
        alExacto()
    }

    val unidad = if (card.kind == CardKind.DURATION) stringResource(R.string.unit_min) else card.unit.orEmpty()
    val estado =
        when {
            dejar -> stringResource(R.string.clean_today)
            limite && suma -> "${card.progress} ${stringResource(R.string.limit_caption, "${card.target}")} $unidad".trim()
            suma -> "${card.progress}/${card.target} $unidad".trim()
            else -> null
        }
    Column(
        modifier
            .fillMaxWidth()
            .testTag("cierre-fila-${card.id}")
            .background(colores.canto, BitoFormas.tarjeta)
            .border(Medidas.linea, colores.contorno, BitoFormas.tarjeta)
            .padding(horizontal = Medidas.e16, vertical = Medidas.e12),
        verticalArrangement = Arrangement.spacedBy(Medidas.e8),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = atenuacion }
                .then(if (decidida) Modifier.testTag("cierre-atenuada-${card.id}") else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Medidas.e12),
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        val n = nivel.value.coerceIn(0f, 1f)
                        if (n > 0f) {
                            drawRect(colores.agua, Offset(0f, size.height * (1f - n)), Size(size.width, size.height * n))
                        }
                    }.border(Medidas.linea, colores.contorno, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (meta >= 1f) Icon(IconosSistema.Check, contentDescription = null, tint = colores.tinta, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(card.name, style = t.t16.fuerte(), color = colores.tinta, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (estado != null) Text(estado, style = t.t14, color = colores.tintaSuave, maxLines = 1)
            }
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Medidas.e8),
            verticalArrangement = Arrangement.spacedBy(Medidas.e4),
        ) {
            if (decidida) {
                BotonTexto(
                    stringResource(R.string.undo),
                    {
                        gesto(Snd.BROTE, Haptica.TICK)
                        alDeshacer()
                    },
                    Modifier.tocable("cierre-deshacer-${card.id}"),
                )
            } else {
                when {
                    dejar -> {
                        Boton(
                            stringResource(R.string.review_clean),
                            {
                                gesto(Snd.GORGOTEO, Haptica.TICKS)
                                scope.launch {
                                    nivel.snapTo(AGUA_DIA_LIMPIO)
                                    nivel.animateTo(1f, if (reducido) tween(Duraciones.REDUCIDO) else Muelles.marcar())
                                }
                                alDecidir()
                            },
                            Modifier.tocable("cierre-limpio-${card.id}"),
                            estilo = EstiloBoton.PRINCIPAL,
                        )
                        BotonTexto(stringResource(R.string.hoy_he_recaido), alRecaer, Modifier.tocable("cierre-recaido-${card.id}"))
                    }
                    limite -> {
                        BotonMas("+${card.step}", { sumar(card.step) }, { exacto() }, Modifier.tocable("cierre-mas-${card.id}"))
                        Boton(
                            stringResource(R.string.review_within_limit),
                            { decidirSinHacer() },
                            Modifier.tocable("cierre-dentro-${card.id}"),
                        )
                    }
                    suma -> {
                        BotonMas("+${card.step}", { sumar(card.step) }, { exacto() }, Modifier.tocable("cierre-mas-${card.id}"))
                        if (card.kind == CardKind.DURATION) {
                            if (card.step != AMOUNT_CORTO) {
                                Boton("+$AMOUNT_CORTO", { sumar(AMOUNT_CORTO) }, Modifier.tocable("cierre-mas5-${card.id}"))
                            }
                            if (card.step != AMOUNT_LARGO) {
                                Boton("+$AMOUNT_LARGO", { sumar(AMOUNT_LARGO) }, Modifier.tocable("cierre-mas15-${card.id}"))
                            }
                            Boton(
                                stringResource(R.string.add_target),
                                {
                                    gesto(Snd.GORGOTEO, Haptica.TICKS)
                                    alFijar(max(card.progress, card.target))
                                },
                                Modifier.tocable("cierre-objetivo-${card.id}"),
                            )
                        }
                        BotonTexto(
                            stringResource(R.string.cierre_total_exacto),
                            { exacto() },
                            Modifier.tocable("cierre-exacto-${card.id}"),
                        )
                        BotonTexto(
                            stringResource(R.string.review_leave),
                            { decidirSinHacer() },
                            Modifier.tocable("cierre-asi-${card.id}"),
                        )
                    }
                    else -> {
                        Boton(
                            stringResource(R.string.review_done),
                            {
                                gesto(Snd.GORGOTEO, Haptica.TICKS)
                                alHecho()
                            },
                            Modifier.tocable("cierre-hecho-${card.id}"),
                            estilo = EstiloBoton.PRINCIPAL,
                        )
                        BotonTexto(
                            stringResource(R.string.review_skip),
                            { decidirSinHacer() },
                            Modifier.tocable("cierre-no-${card.id}"),
                        )
                    }
                }
            }
        }
    }
}

/** El +paso: un toque suma; mantener 450 ms (G28) escribe el total exacto. */
@Composable
private fun BotonMas(
    texto: String,
    alPulsar: () -> Unit,
    alMantener: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val base = LocalViewConfiguration.current
    val config =
        remember(base) {
            object : ViewConfiguration by base {
                override val longPressTimeoutMillis: Long get() = Duraciones.MANTENER.toLong()
            }
        }
    val interaccion = remember { MutableInteractionSource() }
    val descripcion = stringResource(R.string.exact_value_action)
    CompositionLocalProvider(LocalViewConfiguration provides config) {
        Box(
            modifier
                .heightIn(min = Medidas.toqueMinimo)
                .defaultMinSize(minWidth = Medidas.toqueMinimo)
                .combinedClickable(
                    interactionSource = interaccion,
                    indication = null,
                    onClick = alPulsar,
                    onLongClick = alMantener,
                    onLongClickLabel = descripcion,
                    role = Role.Button,
                ).semantics { contentDescription = texto },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .pulsar(interaccion)
                    .heightIn(min = Medidas.boton)
                    .defaultMinSize(minWidth = Medidas.toqueMinimo)
                    .clip(BitoFormas.canto)
                    .background(colores.agua)
                    .border(Medidas.linea, colores.contorno, BitoFormas.canto)
                    .padding(horizontal = Medidas.e18),
                contentAlignment = Alignment.Center,
            ) {
                Text(texto, style = BitoTema.tipos.t16.fuerte(), color = colores.tinta, maxLines = 1)
            }
        }
    }
}

/** Un botón del cierre mide como mínimo 44 dp también de ancho (un «Hecho» corto no se queda en 40). */
private fun Modifier.tocable(etiqueta: String): Modifier = defaultMinSize(minWidth = Medidas.toqueMinimo).testTag(etiqueta)
