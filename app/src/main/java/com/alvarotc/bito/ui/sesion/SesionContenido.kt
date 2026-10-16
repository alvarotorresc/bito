package com.alvarotc.bito.ui.sesion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas
import kotlinx.coroutines.delay

// `mostrar()` del mock: lo que se va se hunde 8 dp en 160 ms; lo nuevo entra 140 ms después, cada pieza desde 12 dp
// abajo en 260 ms con 60 ms + 40 ms por pieza de retraso.
private const val SALE_MS = 160
private const val ENTRA_TRAS_MS = 140L
private val SALE_DY = 8.dp
private const val LLEGA_MS = 260f
private const val LLEGA_RETRASO_MS = 60f
private const val LLEGA_ESCALON_MS = 40f
private val LLEGA_DY = 12.dp

// Más que la última pieza de cualquier bloque (60 + 40 · 12 + 260).
private const val RELOJ_LLEGADA_MS = 1_000

// `.ses-b`: 18 dp arriba y `margen` a los lados.
private val ARRIBA_TIERRA = 18.dp

/** Lo que pintan las piezas y la salida en cada fotograma; solo lo usan las pruebas. */
class SondaBloques {
    var salidaAlfa: Float = 1f
    var salidaDy: Float = 0f
    val piezas: MutableMap<Int, Pair<Float, Float>> = mutableMapOf()
}

/**
 * La tierra de la sesión (`#ses-tierra`): pinta el bloque de [estado] con el cambio de bloque del mock. Cada pieza de
 * un bloque es un hijo directo de su contenido y entra escalonada. El bloque scrollea cuando no cabe (H3). La
 * tierra no pinta el fondo ni la costura: los pone el Inicio, que sabe dónde está el suelo.
 *
 * [alAlto] recibe el alto natural del bloque que entra, en px, con el aire de arriba: con él la cámara sube el suelo
 * si el bloque no cabe debajo (H1).
 */
@Composable
fun SesionContenido(
    estado: EstadoSesionUi,
    modifier: Modifier = Modifier,
    alAlto: (Int) -> Unit = {},
    sonda: SondaBloques? = null,
    contenido: @Composable (BloqueSesion) -> Unit = { BloqueDeSesion(it) },
) {
    val reducido = BitoTema.reducido
    var entrante by remember { mutableStateOf<BloqueSesion?>(null) }
    var saliente by remember { mutableStateOf<BloqueSesion?>(null) }
    val salida = remember { Animatable(0f) }
    val llegada = remember { Animatable(0f) }

    LaunchedEffect(estado.bloque, reducido) {
        val nuevo = estado.bloque
        if (nuevo == entrante) return@LaunchedEffect
        val viejo = entrante
        entrante = null
        if (reducido) {
            saliente = null
        } else if (viejo != null) {
            saliente = viejo
        }
        if (saliente != null) delay(ENTRA_TRAS_MS)
        llegada.snapTo(0f)
        entrante = nuevo
        if (nuevo != null) {
            val fin = if (reducido) Duraciones.REDUCIDO else RELOJ_LLEGADA_MS
            llegada.animateTo(fin.toFloat(), tween(fin, easing = LinearEasing))
        }
    }
    LaunchedEffect(saliente) {
        if (saliente != null) {
            salida.snapTo(0f)
            salida.animateTo(1f, tween(SALE_MS, easing = Curvas.out))
            saliente = null
        }
    }

    val scroll = rememberScrollState()
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().testTag("ses-tierra").verticalScroll(scroll)) {
            Box {
                saliente?.let { viejo ->
                    val dy = with(LocalDensity.current) { SALE_DY.toPx() }
                    Box(
                        Modifier
                            .testTag("ses-b-${viejo.id}")
                            .graphicsLayer {
                                alpha = 1f - salida.value
                                translationY = dy * salida.value
                                sonda?.salidaAlfa = alpha
                                sonda?.salidaDy = translationY
                            }.padding(top = ARRIBA_TIERRA)
                            .padding(horizontal = Medidas.margen),
                    ) { contenido(viejo) }
                }
                entrante?.let { nuevo ->
                    ColumnaBrota(
                        ms = { llegada.value },
                        reducido = reducido,
                        sonda = sonda,
                        modifier =
                            Modifier
                                .testTag("ses-b-${nuevo.id}")
                                .onSizeChanged { alAlto(it.height) }
                                .padding(top = ARRIBA_TIERRA)
                                .padding(horizontal = Medidas.margen),
                    ) { contenido(nuevo) }
                }
            }
        }
    }
}

/**
 * Los hijos en columna, cada uno con su entrada: desde [LLEGA_DY] abajo y opacidad 0, [LLEGA_MS] con `out` y un
 * retraso de 60 ms + 40 ms por posición (en reducido, un fundido de 150 ms sin desplazamiento ni retraso).
 */
@Composable
private fun ColumnaBrota(
    ms: () -> Float,
    reducido: Boolean,
    sonda: SondaBloques?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dy = with(LocalDensity.current) { LLEGA_DY.toPx() }
    Layout(content, modifier) { medibles, c ->
        val sueltas = c.copy(minHeight = 0)
        val piezas = medibles.map { it.measure(sueltas) }
        val ancho = piezas.maxOfOrNull { it.width } ?: c.minWidth
        val alto = piezas.sumOf { it.height }
        layout(maxOf(ancho, c.minWidth), alto) {
            var y = 0
            piezas.forEachIndexed { i, pieza ->
                pieza.placeWithLayer(0, y) {
                    val t = ms()
                    if (reducido) {
                        alpha = (t / Duraciones.REDUCIDO).coerceIn(0f, 1f)
                    } else {
                        val p = ((t - (LLEGA_RETRASO_MS + LLEGA_ESCALON_MS * i)) / LLEGA_MS).coerceIn(0f, 1f)
                        val e = Curvas.out.transform(p)
                        alpha = e
                        translationY = dy * (1f - e)
                    }
                    sonda?.piezas?.set(i, alpha to translationY)
                }
                y += pieza.height
            }
        }
    }
}

/**
 * El contenido de cada bloque. Preparar (Tarea 12), Respirar para entrar y Foco (13), Descanso (14) y Terminada (15)
 * lo llenan cada una en su rama; hasta entonces la tierra del bloque está vacía.
 */
@Composable
internal fun BloqueDeSesion(bloque: BloqueSesion) {
    when (bloque) {
        BloqueSesion.PREPARAR -> Unit
        BloqueSesion.RESPIRAR -> Unit
        BloqueSesion.FOCO -> Unit
        BloqueSesion.DESCANSO -> DescansoContenido()
        BloqueSesion.TERMINADA -> Unit
    }
}
