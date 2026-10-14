package com.alvarotc.bito.ui.today

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.ui.inicio.textoHabi
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonTexto
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

private const val DESPLAZA_LLEGADA_DP = 14

/** La propuesta de Habi en Hoy (spec §5.4): «Habi» en canto, la frase y, en fila, Vale, Ahora y Hoy no. */
@Composable
fun TarjetaHabi(
    propuesta: Propuesta?,
    alResponder: (Respuesta) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    // La última propuesta se retiene para que la tarjeta pueda encoger con su contenido a la vista.
    val retenida = remember { arrayOfNulls<Propuesta>(1) }
    if (propuesta != null) retenida[0] = propuesta
    // Lo que acaba de pulsar el usuario: el cambio que sigue es un relevo, no una llegada.
    var respondida by remember { mutableStateOf<Respuesta?>(null) }
    val brote = remember { Animatable(1f) }
    val anterior = remember { arrayOf(propuesta?.id) }

    LaunchedEffect(propuesta?.id) {
        val id = propuesta?.id
        if (id == null) respondida = null
        if (id != null && id != anterior[0]) {
            if (respondida == null) {
                gesto(Snd.BROTE, Haptica.TICK)
                brote.snapTo(0f)
                brote.animateTo(1f, tween(if (reducido) Duraciones.REDUCIDO else ENTRAR_MS, easing = Curvas.drawer))
            }
            respondida = null
        }
        anterior[0] = id
    }

    Column(modifier.fillMaxWidth().heightIn(min = 22.dp)) {
        AnimatedVisibility(
            visible = propuesta != null,
            enter =
                if (reducido) {
                    fadeIn(tween(Duraciones.REDUCIDO))
                } else {
                    expandVertically(tween(ENTRAR_MS, easing = Curvas.drawer)) + fadeIn(tween(ENTRAR_MS, easing = Curvas.drawer))
                },
            exit =
                if (reducido) {
                    fadeOut(tween(Duraciones.REDUCIDO))
                } else {
                    shrinkVertically(tween(ENTRAR_MS, easing = Curvas.drawer)) + fadeOut(tween(Duraciones.SALIR))
                },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag("tarjeta-habi")
                    .graphicsLayer {
                        val v = brote.value
                        alpha = 0.4f + 0.6f * v
                        if (!reducido) translationY = (1f - v) * DESPLAZA_LLEGADA_DP.dp.toPx()
                    }.background(colores.canto, BitoFormas.tarjeta)
                    .border(Medidas.linea, colores.contorno, BitoFormas.tarjeta)
                    .padding(Medidas.e16),
            ) {
                Text(stringResource(R.string.hoy_habi), style = BitoTema.tipos.t14.fuerte(), color = colores.tintaSuave)
                val enBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !reducido
                AnimatedContent(
                    targetState = retenida[0],
                    contentKey = { it?.id },
                    transitionSpec = {
                        val sueltaAcciones = respondida == Respuesta.HOY_NO
                        val entra = if (reducido) Duraciones.REDUCIDO else Duraciones.RELEVO_ENTRA
                        val sale = if (reducido) Duraciones.REDUCIDO else Duraciones.RELEVO_SALE
                        val fundido =
                            if (sueltaAcciones && !reducido) {
                                // Hoy no: la tarjeta se hunde y en su sitio brota la frase corta de Habi.
                                fadeIn(tween(HOY_NO_ENTRA_MS, delayMillis = Duraciones.SALIR, easing = Curvas.out)) togetherWith
                                    (
                                        slideOutVertically(tween(Duraciones.SALIR, easing = Curvas.out)) { it / 6 } +
                                            fadeOut(tween(Duraciones.SALIR, easing = Curvas.out))
                                    )
                            } else {
                                fadeIn(tween(entra, delayMillis = sale)) togetherWith fadeOut(tween(sale))
                            }
                        fundido.using(SizeTransform(clip = false) { _, _ -> tween(HOY_NO_ENTRA_MS, easing = Curvas.drawer) })
                    },
                    label = "relevoHabi",
                ) { p ->
                    val desenfoque by transition.animateDp(label = "relevoBlur") {
                        if (it == EnterExitState.Visible) 0.dp else 2.dp
                    }
                    if (p != null) {
                        Column(Modifier.then(if (enBlur) Modifier.blur(desenfoque) else Modifier)) {
                            Text(
                                textoHabi(p.texto),
                                style = BitoTema.tipos.t19,
                                color = colores.tinta,
                                modifier = Modifier.padding(top = Medidas.e4),
                            )
                            if (p.acciones.isNotEmpty()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = Medidas.e12),
                                    horizontalArrangement = Arrangement.spacedBy(Medidas.e8),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (Respuesta.VALE in p.acciones) {
                                        Boton(
                                            p.etiquetaVale?.let { textoHabi(it) } ?: stringResource(R.string.hoy_vale),
                                            {
                                                respondida = Respuesta.VALE
                                                gesto(Snd.BROTE, Haptica.TICK)
                                                alResponder(Respuesta.VALE)
                                            },
                                            estilo = EstiloBoton.PRINCIPAL,
                                        )
                                    }
                                    if (Respuesta.AHORA in p.acciones) {
                                        BotonTexto(
                                            stringResource(R.string.hoy_ahora),
                                            {
                                                gesto(h = Haptica.TICK)
                                                alResponder(Respuesta.AHORA)
                                            },
                                        )
                                    }
                                    if (Respuesta.HOY_NO in p.acciones) {
                                        BotonTexto(
                                            stringResource(R.string.task_not_today),
                                            {
                                                respondida = Respuesta.HOY_NO
                                                gesto(Snd.HUNDIR, Haptica.FALL)
                                                alResponder(Respuesta.HOY_NO)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val ENTRAR_MS = 280
private const val HOY_NO_ENTRA_MS = 260
