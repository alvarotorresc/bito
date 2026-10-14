package com.alvarotc.bito.ui.premios

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.camino.HojaLogro
import com.alvarotc.bito.ui.camino.LogroUi
import com.alvarotc.bito.ui.camino.LogrosArte
import com.alvarotc.bito.ui.camino.ModoLogro
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.componentes.Emblema
import com.alvarotc.bito.ui.sistema.componentes.FamiliaLogro
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

private const val SALEN_MONEDAS_MS = 380L
private const val ESPERA_JARDIN_MS = 900L

/**
 * Las celebraciones del Inicio (pieza 3, §6.1): recoge `celebraciones.actual` y las pinta una a una (G14). Compone
 * **una sola** [CapaParticulas], siempre, para que la gota y los granos vuelen aunque no haya nada en la cola y nunca
 * haya dos capas recogiendo `registro.gotas`. [personalidad] y [nombre] ponen la voz de Habi en la frase de las
 * vetas de semana y de mes; sin ellos hablan en neutro.
 */
@Composable
fun PremiosEnInicio(
    celebraciones: CelebracionesViewModel,
    registro: RegistroAnclas,
    costuraY: () -> Float,
    alVerEnJardin: (GardenSpecial?) -> Unit,
    alJardin: (llegando: GardenSpecial?, creciendo: Int?) -> Unit,
    alAsomarCabecera: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    personalidad: Personality = Personality.NEUTRA,
    nombre: String = "",
) {
    val actual by celebraciones.actual.collectAsStateWithLifecycle()
    val actualAhora by rememberUpdatedState(actual)
    val jardin by rememberUpdatedState(alJardin)
    val asomar by rememberUpdatedState(alAsomarCabecera)
    val reducido = BitoTema.reducido
    var monedasDeLaVeta by remember { mutableStateOf<Momento.Monedas?>(null) }
    val franja = remember { Animatable(0f) }

    // La cabecera asoma mientras llegan las monedas, las suyas o las de una veta.
    LaunchedEffect(actual) {
        if (actual is Momento.Monedas || actual is Momento.Veta) {
            asomar(true)
            try {
                awaitCancellation()
            } finally {
                asomar(false)
            }
        }
    }

    LaunchedEffect(actual) {
        when (val m = actual) {
            is Momento.Veta -> {
                try {
                    franja.snapTo(0f)
                    launch { franja.animateTo(1f, if (reducido) tween(Duraciones.REDUCIDO) else Muelles.hoja()) }
                    delay(if (reducido) Duraciones.REDUCIDO.toLong() else SALEN_MONEDAS_MS)
                    monedasDeLaVeta = Momento.Monedas(ORIGEN_VETA, m.bitos, min(m.bitos, TopesParticulas.MONEDAS), m.lote)
                    snapshotFlow { monedasDeLaVeta }.first { it == null }
                    delay(Duraciones.FRANJA.toLong())
                    franja.animateTo(0f, if (reducido) tween(Duraciones.REDUCIDO) else Muelles.hundir())
                } finally {
                    monedasDeLaVeta = null
                }
                celebraciones.terminado(m)
            }
            is Momento.Especial -> {
                try {
                    jardin(m.especial, null)
                    delay(ESPERA_JARDIN_MS)
                } finally {
                    jardin(null, null)
                }
                celebraciones.terminado(m)
            }
            is Momento.Etapa -> {
                try {
                    jardin(null, m.parcela)
                    delay(ESPERA_JARDIN_MS)
                } finally {
                    jardin(null, null)
                }
                celebraciones.terminado(m)
            }
            is Momento.Monedas, is Momento.Logro, null -> Unit
        }
    }

    Box(modifier.fillMaxSize()) {
        CapaParticulas(
            registro,
            monedas = (actual as? Momento.Monedas) ?: monedasDeLaVeta,
            alLlegarMoneda = {},
            alTerminar = {
                val m = actualAhora
                if (m is Momento.Monedas) celebraciones.terminado(m) else monedasDeLaVeta = null
            },
        )
        when (val m = actual) {
            is Momento.Veta ->
                key(m) {
                    Box(
                        Modifier
                            .offset { IntOffset(0, costuraY().roundToInt()) }
                            .fillMaxWidth()
                            .then(
                                if (reducido) {
                                    Modifier.graphicsLayer { alpha = franja.value }
                                } else {
                                    Modifier.drawWithContent {
                                        clipRect(
                                            bottom = size.height * franja.value,
                                        ) { this@drawWithContent.drawContent() }
                                    }
                                },
                            ),
                    ) {
                        VetaDeCola(m, personalidad, nombre)
                    }
                }
            is Momento.Logro ->
                key(m) {
                    val def = BadgeCatalog.all.firstOrNull { it.id == m.badgeId }
                    if (def == null) {
                        LaunchedEffect(m) { celebraciones.terminado(m) }
                    } else {
                        val especial = GardenSpecial.entries.firstOrNull { it.badgeId == m.badgeId }
                        val dia = Instant.ofEpochMilli(m.unlockedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay().toInt()
                        HojaLogro(
                            abierta = true,
                            logro = LogroUi(def, conseguido = dia, progreso = null, especial = especial),
                            modo = ModoLogro.NUEVO,
                            // La hoja ya espera su salida antes de llamar aquí (`cerrarY`); al terminar de salir llama a `alCerrar`.
                            alVerEnJardin = { alVerEnJardin(especial) },
                            alCerrar = { celebraciones.terminado(m) },
                        )
                    }
                }
            else -> Unit
        }
    }
}

/** Los textos y el arte de cada veta (tabla de §6.3). */
@Composable
private fun VetaDeCola(
    m: Momento.Veta,
    personalidad: Personality,
    nombre: String,
) {
    val respaldo = stringResource(R.string.habi_name_fallback)
    val frasePerfecta = stringResource(HabiVoice.perfectDayRes(personalidad), nombre.ifBlank { respaldo })
    when (m.tipo) {
        TipoVeta.DIA ->
            Veta(m.tipo, stringResource(R.string.premios_dia_completo), stringResource(R.string.premios_dia_frase), m.bitos, {
                ArteLogro("perfect-day-1")
            })
        TipoVeta.SEMANA ->
            Veta(m.tipo, stringResource(R.string.premios_semana_completa), frasePerfecta, m.bitos, { ArteLogro("perfect-week") })
        TipoVeta.MES ->
            Veta(m.tipo, stringResource(R.string.premios_mes_completo), frasePerfecta, m.bitos, { ArteLogro("perfect-month") })
        TipoVeta.RACHA -> {
            val n = m.racha ?: 0
            Veta(m.tipo, pluralStringResource(R.plurals.premios_dias_seguidos, n, n), m.nombre.orEmpty(), m.bitos, {
                // Un logro nuevo se enciende aquí; si ya lo tenías, entra encendido sin celebrar.
                Emblema(
                    FamiliaLogro.RACHAS,
                    progreso = if (m.logroNuevo && n > 0) (n - 1f) / n else 1f,
                    encendido = true,
                    tamano = 52.dp,
                    celebrar = m.logroNuevo,
                ) { ArteLogro("streak-$n") }
            })
        }
    }
}

@Composable
private fun ArteLogro(badgeId: String) {
    val recurso = LogrosArte.de(badgeId) ?: return
    Image(painterResource(recurso), contentDescription = null, modifier = Modifier.fillMaxSize())
}
