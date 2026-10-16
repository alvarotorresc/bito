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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.propuestas.Accion
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val alcance = rememberCoroutineScope()
    // La última propuesta se retiene para que la tarjeta pueda encoger con su contenido a la vista.
    val retenida = remember { arrayOfNulls<Propuesta>(1) }
    if (propuesta != null) retenida[0] = propuesta
    // Lo que acaba de pulsar el usuario: el cambio que sigue es un relevo, no una llegada.
    var respondida by remember { mutableStateOf<Respuesta?>(null) }
    // «Vale» sin hora (día duro): los botones se pliegan y la confirmación se funde. «Hecho»: la tarjeta encoge sin texto.
    var valeDuro by remember { mutableStateOf(false) }
    var sinTexto by remember { mutableStateOf(false) }
    var altoContenido by remember { mutableIntStateOf(0) }
    val brote = remember { Animatable(1f) }
    val anterior = remember { arrayOf(propuesta?.id) }

    LaunchedEffect(propuesta?.id) {
        val id = propuesta?.id
        // «Hecho»: la tarjeta se va y se hunde (spec §9, «Vuelta, Hecho»).
        if (id != anterior[0] && respondida == Respuesta.HECHO) gesto(Snd.HUNDIR, Haptica.FALL)
        if (id == null) respondida = null
        if (id != null && id != anterior[0]) {
            valeDuro = false
            sinTexto = false
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
                if (sinTexto) {
                    // Hecho: el hueco conserva su alto, vacío, y es la tarjeta la que encoge.
                    Spacer(Modifier.height(with(LocalDensity.current) { altoContenido.toDp() }))
                } else {
                    Column(Modifier.onSizeChanged { altoContenido = it.height }) {
                        Text(stringResource(R.string.hoy_habi), style = BitoTema.tipos.t14.fuerte(), color = colores.tintaSuave)
                        val enBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !reducido
                        AnimatedContent(
                            targetState = retenida[0],
                            contentKey = { it?.id },
                            transitionSpec = {
                                val sueltaAcciones = respondida == Respuesta.HOY_NO || respondida == Respuesta.LUEGO
                                val entra =
                                    if (reducido) {
                                        Duraciones.REDUCIDO
                                    } else if (valeDuro) {
                                        CONFIRMACION_DURA_MS
                                    } else {
                                        Duraciones.RELEVO_ENTRA
                                    }
                                val sale =
                                    if (reducido) {
                                        Duraciones.REDUCIDO
                                    } else if (valeDuro) {
                                        PLEGAR_BOTONES_MS
                                    } else {
                                        Duraciones.RELEVO_SALE
                                    }
                                val fundido =
                                    if (sueltaAcciones && !reducido) {
                                        // Hoy no, Luego, Ahora no, Todavía no: la tarjeta se hunde y en su sitio brota la frase corta de Habi.
                                        fadeIn(tween(HOY_NO_ENTRA_MS, delayMillis = HUNDIR_TARJETA_MS, easing = Curvas.out)) togetherWith
                                            (
                                                slideOutVertically(tween(HUNDIR_TARJETA_MS, easing = Curvas.out)) { it / 6 } +
                                                    fadeOut(tween(HUNDIR_TARJETA_MS, easing = Curvas.out))
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
                                        AnimatedVisibility(
                                            visible = !valeDuro,
                                            exit =
                                                if (reducido) {
                                                    fadeOut(tween(Duraciones.REDUCIDO))
                                                } else {
                                                    shrinkVertically(tween(PLEGAR_BOTONES_MS, easing = Curvas.out)) +
                                                        fadeOut(tween(PLEGAR_BOTONES_MS, easing = Curvas.out))
                                                },
                                        ) {
                                            FilaAcciones(p) { accion ->
                                                respondida = accion.respuesta
                                                when {
                                                    accion.respuesta == Respuesta.HOY_NO || accion.respuesta == Respuesta.LUEGO ->
                                                        gesto(Snd.HUNDIR, Haptica.FALL)
                                                    accion.respuesta == Respuesta.VALE && esDiaDuro(p) -> {
                                                        valeDuro = true
                                                        gesto(Snd.TIC, Haptica.CONFIRM)
                                                    }
                                                    accion.respuesta == Respuesta.HECHO -> {
                                                        // Marca (gorgoteo, ticks); Habi suena al saltar con el jardín.
                                                        sinTexto = true
                                                        gesto(Snd.GORGOTEO, Haptica.TICKS)
                                                        alcance.launch {
                                                            delay(SALTO_HABI_MS)
                                                            gesto(Snd.HABI)
                                                        }
                                                    }
                                                    accion.principal -> gesto(Snd.BROTE, Haptica.TICK)
                                                    else -> gesto(h = Haptica.TICK)
                                                }
                                                alResponder(accion.respuesta)
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
    }
}

/** Si la propuesta es la de un día duro: su id estable empieza por la situación (`SITUACION:ref:día`). */
internal fun esDiaDuro(propuesta: Propuesta): Boolean = propuesta.id.startsWith("${Situacion.DIA_DURO.name}:")

/** Los botones de la propuesta, en fila y en su orden: el principal relleno, los demás como texto. */
@Composable
private fun FilaAcciones(
    propuesta: Propuesta,
    alPulsar: (Accion) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = Medidas.e12),
        horizontalArrangement = Arrangement.spacedBy(Medidas.e8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        propuesta.acciones.forEach { accion ->
            val etiqueta = textoHabi(accion.etiqueta)
            val marca = Modifier.testTag("habi-${accion.respuesta.name.lowercase()}")
            if (accion.principal) {
                Boton(etiqueta, { alPulsar(accion) }, marca, estilo = EstiloBoton.PRINCIPAL)
            } else {
                BotonTexto(etiqueta, { alPulsar(accion) }, marca)
            }
        }
    }
}

private const val ENTRAR_MS = 280
private const val HOY_NO_ENTRA_MS = 260

// Sin token en Duraciones: el hundimiento de la tarjeta (240 ms, curva out) y los dos tiempos del «Vale» de un día duro.
private const val HUNDIR_TARJETA_MS = 240
private const val PLEGAR_BOTONES_MS = 220
private const val CONFIRMACION_DURA_MS = 200

// Sin token: el jardín crece y Habi salta 1,1 s después de marcar la vuelta (spec §5.6).
private const val SALTO_HABI_MS = 1_100L
