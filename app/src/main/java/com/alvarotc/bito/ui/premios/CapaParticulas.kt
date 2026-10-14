package com.alvarotc.bito.ui.premios

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Cuántas partículas de cada clase caben a la vez (Review Focus 4). */
internal object TopesParticulas {
    const val GOTAS = 6
    const val MONEDAS = 5
    const val GRANOS = 14
}

/** El origen de las monedas de una veta: la veta registra su propio ancla con esta clave. */
internal const val ORIGEN_VETA = "veta"

private const val APAGAR_MS = 120

/**
 * Bézier cuadrática del centro de [origen] al centro de la base de [destino] (o a su centro con [alCentro]), con el
 * control por encima de los dos. [torcer] mueve el control de lado; sin él se tuerce 30 dp hacia fuera.
 */
internal fun trayectoria(
    origen: Rect,
    destino: Rect,
    t: Float,
    densidad: Float = 1f,
    alCentro: Boolean = false,
    torcer: Float? = null,
): Offset {
    val p0 = origen.center
    val p2 = if (alCentro) destino.center else Offset(destino.center.x, destino.bottom)
    val lado = torcer ?: ((if (p2.x > p0.x) -30f else 30f) * densidad)
    val control = Offset((p0.x + p2.x) / 2f + lado, min(p0.y, p2.y) - 90f * densidad)
    val q = 1f - t
    return Offset(
        q * q * p0.x + 2f * q * t * control.x + t * t * p2.x,
        q * q * p0.y + 2f * q * t * control.y + t * t * p2.y,
    )
}

internal class GotaVolando(val desde: Rect, val hasta: Rect, val origen: String) {
    val t = Animatable(0f)
    val apagado = Animatable(1f)
    var vuelo: Job? = null
}

internal class Salpicadura(val en: Offset) {
    val t = Animatable(0f)
}

internal class GranoCayendo(val x: Float, val y: Float, val deriva: Float, val caida: Float, val radio: Float) {
    val t = Animatable(0f)
}

internal class MonedaVolando(val desde: Rect, val hasta: Rect, val torcer: Float) {
    val t = Animatable(0f)
}

internal class EtiquetaMas(val texto: String, val en: Offset) {
    val t = Animatable(0f)
}

/** Lo que la capa tiene ahora en el aire. Se lee en la fase de dibujo: un fotograma no recompone nada. */
internal class EstadoParticulas {
    val gotas = mutableStateListOf<GotaVolando>()
    val salpicaduras = mutableStateListOf<Salpicadura>()
    val granos = mutableStateListOf<GranoCayendo>()
    val monedas = mutableStateListOf<MonedaVolando>()
    val etiquetas = mutableStateListOf<EtiquetaMas>()
}

/**
 * La capa de gotas (G1, G3), granos (G6) y monedas (G4), a pantalla completa y sin recibir toques. Vive siempre
 * en el Inicio: `registro.gotas` no tiene réplica, y una gota emitida sin capa compuesta se perdería.
 * [alTerminar] se refiere siempre a la tanda de [monedas] actual; las gotas y los granos nunca lo llaman.
 */
@Composable
fun CapaParticulas(
    registro: RegistroAnclas,
    monedas: Momento.Monedas?,
    alLlegarMoneda: () -> Unit,
    alTerminar: () -> Unit,
    modifier: Modifier = Modifier,
) = CapaParticulasConEstado(registro, monedas, alLlegarMoneda, alTerminar, modifier, remember { EstadoParticulas() })

@Composable
internal fun CapaParticulasConEstado(
    registro: RegistroAnclas,
    monedas: Momento.Monedas?,
    alLlegarMoneda: () -> Unit,
    alTerminar: () -> Unit,
    modifier: Modifier,
    estado: EstadoParticulas,
) {
    val gesto = rememberGesto()
    val reducido by rememberUpdatedState(BitoTema.reducido)
    val llegar by rememberUpdatedState(alLlegarMoneda)
    val terminar by rememberUpdatedState(alTerminar)
    val densidad = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    // Gotas y granos: no dependen de `monedas`, así que un cambio de momento no los reinicia.
    LaunchedEffect(registro) {
        registro.gotas.collect { evento ->
            when (evento) {
                is EventoGota.Sale -> {
                    gesto(Snd.GORGOTEO, Haptica.TICKS)
                    val desde = registro.posiciones[evento.origen]
                    val hasta = registro.posiciones[ClavesAncla.PARCELA]
                    when {
                        reducido ->
                            scope.launch {
                                delay(Duraciones.REDUCIDO.toLong())
                                gesto(Snd.LLEGADA, Haptica.TICK)
                            }
                        desde == null || hasta == null ->
                            scope.launch {
                                delay((Duraciones.GOTA_RETRASO + Duraciones.GOTA).toLong())
                                gesto(Snd.LLEGADA, Haptica.TICK)
                            }
                        estado.gotas.size < TopesParticulas.GOTAS -> {
                            val gota = GotaVolando(desde, hasta, evento.origen)
                            estado.gotas += gota
                            gota.vuelo =
                                scope.launch {
                                    gota.t.animateTo(1f, tween(Duraciones.GOTA, Duraciones.GOTA_RETRASO, Curvas.gota))
                                    estado.gotas -= gota
                                    gesto(Snd.LLEGADA, Haptica.TICK)
                                    val salpicadura = Salpicadura(Offset(hasta.center.x, hasta.bottom))
                                    estado.salpicaduras += salpicadura
                                    try {
                                        salpicadura.t.animateTo(1f, tween(Duraciones.SALPICADURA, easing = Curvas.out))
                                    } finally {
                                        estado.salpicaduras -= salpicadura
                                    }
                                }
                        }
                    }
                }
                is EventoGota.Apaga -> {
                    val gota = estado.gotas.lastOrNull { it.origen == evento.origen && it.apagado.value == 1f }
                    if (gota != null) {
                        gota.vuelo?.cancel()
                        scope.launch {
                            gota.apagado.animateTo(0f, tween(APAGAR_MS, easing = Curvas.out))
                            estado.gotas -= gota
                        }
                    }
                }
                is EventoGota.Granos -> {
                    // El rectángulo se copia ahora: la fila de una tarea terminada sale de la composición a los 340 ms.
                    val rect = registro.posiciones[evento.origen]
                    val libres = TopesParticulas.GRANOS - estado.granos.size
                    if (!reducido && rect != null && libres > 0) {
                        repeat(libres) { i ->
                            val grano =
                                GranoCayendo(
                                    x = rect.center.x + (Random.nextFloat() - 0.5f) * rect.width * 0.8f,
                                    y = rect.center.y,
                                    deriva = (Random.nextFloat() - 0.5f) * 24f * densidad,
                                    caida = (90f + Random.nextFloat() * 90f) * densidad,
                                    radio = (2f + Random.nextFloat() * 1.6f) * densidad,
                                )
                            estado.granos += grano
                            scope.launch {
                                try {
                                    grano.t.animateTo(
                                        1f,
                                        tween(Duraciones.GRANOS, delayMillis = i * Duraciones.GRANOS_ESCALON, easing = Curvas.cae),
                                    )
                                } finally {
                                    estado.granos -= grano
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Monedas: una tanda por Momento.Monedas.
    LaunchedEffect(monedas) {
        val m = monedas ?: return@LaunchedEffect
        val n = min(m.monedas, TopesParticulas.MONEDAS)
        if (n <= 0) {
            terminar()
            return@LaunchedEffect
        }
        if (reducido) {
            gesto(Snd.MONEDA, Haptica.TICK)
            repeat(n) { llegar() }
            delay(Duraciones.REDUCIDO.toLong())
            terminar()
            return@LaunchedEffect
        }
        val desde = registro.posicionO(m.origen)
        val hasta = registro.posiciones[ClavesAncla.SALDO]
        if (desde == null || hasta == null) {
            gesto(Snd.MONEDA, Haptica.TICK)
            terminar()
            return@LaunchedEffect
        }
        if (m.origen != ORIGEN_VETA) {
            // El «+N» dura más que el vuelo: va en el ámbito de la capa para que no se corte al terminar la tanda.
            val etiqueta = EtiquetaMas("+${m.bitos}", Offset(desde.center.x, desde.top))
            estado.etiquetas += etiqueta
            scope.launch {
                try {
                    etiqueta.t.animateTo(1f, tween(Duraciones.MAS_N, easing = Curvas.out))
                } finally {
                    estado.etiquetas -= etiqueta
                }
            }
        }
        coroutineScope {
            repeat(n) { i ->
                launch {
                    val moneda = MonedaVolando(desde, hasta, (i - (n - 1) / 2f) * 18f * densidad)
                    estado.monedas += moneda
                    try {
                        moneda.t.animateTo(1f, tween(Duraciones.MONEDA, i * Duraciones.MONEDA_ESCALON, Curvas.drawer))
                    } finally {
                        estado.monedas -= moneda
                    }
                    gesto(Snd.MONEDA, Haptica.TICK, intensidad = if (i == 0) 1f else 0.6f)
                    llegar()
                }
            }
        }
        terminar()
    }

    val colores = BitoTema.colores
    val medidor = rememberTextMeasurer()
    val estiloMas = BitoTema.tipos.t19.fuerte()
    val imagenMoneda = ImageBitmap.imageResource(R.drawable.coin_small)
    Canvas(modifier.fillMaxSize()) {
        pintarGotas(estado, colores.agua, colores.aguaHonda)
        pintarGranos(estado, colores.tMedia2)
        pintarMonedas(estado, imagenMoneda)
        for (e in estado.etiquetas) {
            val p = e.t.value
            val medido = medidor.measure(e.texto, estiloMas)
            val alfa = if (p < 0.25f) p / 0.25f else 1f - (p - 0.25f) / 0.75f
            drawText(
                medido,
                color = colores.oroTexto.copy(alpha = alfa.coerceIn(0f, 1f)),
                topLeft = Offset(e.en.x - medido.size.width / 2f, e.en.y - 38.dp.toPx() * p - medido.size.height),
            )
        }
    }
}

private fun DrawScope.pintarGotas(
    estado: EstadoParticulas,
    agua: Color,
    honda: Color,
) {
    for (g in estado.gotas) {
        val p = g.t.value
        if (p <= 0f) continue
        val centro = trayectoria(g.desde, g.hasta, p, density)
        val escala = 0.8f + 0.4f * sin(PI.toFloat() * p)
        val radio = 6.dp.toPx() * escala
        drawCircle(agua.copy(alpha = g.apagado.value), radio, centro)
        drawCircle(honda.copy(alpha = g.apagado.value), radio, centro, style = Stroke(1.dp.toPx()))
    }
    for (s in estado.salpicaduras) {
        val p = s.t.value
        for (i in 0 until 4) {
            val angulo = -PI.toFloat() / 2f + (i - 1.5f) * 0.6f
            val v = (16f + i * 3f) * density
            val punto = Offset(s.en.x + cos(angulo) * v * p, s.en.y + sin(angulo) * v * p + 18f * density * p * p)
            drawCircle(agua.copy(alpha = 1f - p), 3.dp.toPx() * (1f - 0.5f * p), punto)
        }
    }
}

private fun DrawScope.pintarGranos(
    estado: EstadoParticulas,
    tierra: Color,
) {
    for (g in estado.granos) {
        val p = g.t.value
        if (p <= 0f) continue
        val alfa = if (p < 0.6f) 1f else (1f - p) / 0.4f
        drawCircle(tierra.copy(alpha = alfa.coerceIn(0f, 1f)), g.radio, Offset(g.x + g.deriva * p, g.y + g.caida * p))
    }
}

private fun DrawScope.pintarMonedas(
    estado: EstadoParticulas,
    imagen: ImageBitmap,
) {
    for (m in estado.monedas) {
        val p = m.t.value
        if (p <= 0f) continue
        val centro = trayectoria(m.desde, m.hasta, p, density, alCentro = true, torcer = m.torcer)
        val escala = if (p < 0.12f) 0.9f + p * 0.8f else 1f - 0.3f * p
        val lado = (22.dp.toPx() * escala).toInt().coerceAtLeast(1)
        val alfa = if (p > 0.94f) (1f - p) / 0.06f else 1f
        drawImage(
            imagen,
            dstOffset = IntOffset((centro.x - lado / 2f).toInt(), (centro.y - lado / 2f).toInt()),
            dstSize = IntSize(lado, lado),
            alpha = alfa.coerceIn(0f, 1f),
        )
    }
}
