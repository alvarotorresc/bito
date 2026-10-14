package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.BitoTiposBase
import com.alvarotc.bito.ui.sistema.Escena
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ARCOS = 96
private val TRAZO_ACTIVO = 16.dp
private val TRAZO_APAGADO = 8.dp
private val RADIO_ASTRO = 11.dp

/** G29: reloj de 24 h con el color del cielo. [paso] 5 o 30; fuera de [desde]–[hasta] se apaga y el astro se queda en el extremo. */
@Composable
fun RelojCielo(
    minutos: Int,
    alCambiar: (Int) -> Unit,
    modifier: Modifier = Modifier,
    paso: Int = 5,
    desde: Int = 0,
    hasta: Int = RelojLogica.DIA - 1,
    descripcion: String = "",
) {
    val reducido = BitoTema.reducido
    val colores = BitoTema.colores
    val gesto = rememberGesto()
    val scope = rememberCoroutineScope()
    val minutosActual by rememberUpdatedState(minutos)
    val alCambiarActual by rememberUpdatedState(alCambiar)

    // Posición del astro sin dar vueltas: valor sin envolver, para que viaje por el camino corto.
    val astro = remember { Animatable(minutos.toFloat()) }
    LaunchedEffect(minutos) {
        val delta = RelojLogica.caminoCorto(astro.value.roundToInt().mod(RelojLogica.DIA), minutos)
        astro.snapTo(astro.value.roundToInt() + delta.toFloat())
    }

    fun mover(nuevo: Int) {
        val anterior = minutosActual
        if (nuevo == anterior) return
        alCambiarActual(nuevo)
        gesto(h = Haptica.SEG)
        if (nuevo / 60 != anterior / 60) gesto(Snd.TIC, Haptica.LOW)
    }

    fun desplazar(delta: Int) {
        mover(RelojLogica.acotar((minutosActual + delta).mod(RelojLogica.DIA), desde, hasta))
    }

    fun minutoDe(
        p: Offset,
        centro: Offset,
    ): Int {
        val grados = RelojLogica.anguloDePunto(p.x - centro.x, p.y - centro.y)
        return RelojLogica.acotar(RelojLogica.minutoDeAngulo(grados, paso), desde, hasta)
    }

    Box(
        modifier
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (e.key) {
                        Key.DirectionUp, Key.DirectionRight -> desplazar(paso).let { true }
                        Key.DirectionDown, Key.DirectionLeft -> desplazar(-paso).let { true }
                        else -> false
                    }
                }
            }.focusable()
            .semantics {
                contentDescription = descripcion
                stateDescription = "%d:%02d".format(minutos / 60, minutos % 60)
                progressBarRangeInfo = ProgressBarRangeInfo(minutos.toFloat(), minOf(desde, hasta).toFloat()..maxOf(desde, hasta).toFloat())
                setProgress { v ->
                    val m = RelojLogica.acotar((v / paso).roundToInt() * paso, desde, hasta)
                    alCambiarActual(m)
                    true
                }
            }.pointerInput(paso, desde, hasta) {
                detectTapGestures { p ->
                    val centro = Offset(size.width / 2f, size.height / 2f)
                    val destino = minutoDe(p, centro)
                    gesto(h = Haptica.CLICK)
                    scope.launch {
                        val delta = RelojLogica.caminoCorto(astro.value.roundToInt().mod(RelojLogica.DIA), destino)
                        astro.animateTo(astro.value.roundToInt() + delta.toFloat(), Muelles.reloj(reducido))
                        alCambiarActual(destino)
                        gesto(Snd.TIC, Haptica.TICK)
                    }
                }
            }.pointerInput(paso, desde, hasta) {
                val centro = Offset(size.width / 2f, size.height / 2f)
                detectDragGestures(
                    onDragStart = { p ->
                        gesto(h = Haptica.CLICK)
                        mover(minutoDe(p, centro))
                    },
                    onDragEnd = { gesto(h = Haptica.TICK) },
                    onDragCancel = { gesto(h = Haptica.TICK) },
                ) { change, _ ->
                    change.consume()
                    mover(minutoDe(change.position, centro))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val activo = TRAZO_ACTIVO.toPx()
                    val apagado = TRAZO_APAGADO.toPx()
                    val margen = activo / 2f + RADIO_ASTRO.toPx() / 2f
                    val lado = size.minDimension - margen * 2f
                    val esquina = Offset((size.width - lado) / 2f, (size.height - lado) / 2f)
                    val barrido = 360f / ARCOS
                    val dentro: (Int) -> Boolean = { m -> m in minOf(desde, hasta)..maxOf(desde, hasta) }
                    onDrawBehind {
                        for (i in 0 until ARCOS) {
                            val medio = (i * RelojLogica.DIA / ARCOS) + RelojLogica.DIA / ARCOS / 2
                            val encendido = dentro(medio)
                            drawArc(
                                color = RelojLogica.colorCielo(medio).copy(alpha = if (encendido) 1f else 0.35f),
                                startAngle = i * barrido - 90f,
                                sweepAngle = barrido + 0.6f,
                                useCenter = false,
                                topLeft = esquina,
                                size = Size(lado, lado),
                                style = Stroke(if (encendido) activo else apagado, cap = StrokeCap.Butt),
                            )
                        }
                    }
                },
        ) {
            val margen = TRAZO_ACTIVO.toPx() / 2f + RADIO_ASTRO.toPx() / 2f
            val radio = (size.minDimension - margen * 2f) / 2f
            val grados = RelojLogica.anguloDeMinuto(astro.value.roundToInt())
            val rad = (grados - 90f) * PI.toFloat() / 180f
            val centro = Offset(size.width / 2f, size.height / 2f)
            val pos = Offset(centro.x + radio * cos(rad), centro.y + radio * sin(rad))
            val relleno = lerp(Escena.luna, Escena.sol, RelojLogica.mezclaSol(astro.value.roundToInt().mod(RelojLogica.DIA)))
            drawCircle(relleno, RADIO_ASTRO.toPx(), pos)
            drawCircle(colores.canto, RADIO_ASTRO.toPx(), pos, style = Stroke(2.dp.toPx()))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CifraRodillo(minutos / 60, estilo = BitoTiposBase.t52.cifra(), color = colores.tinta)
            Text(":", style = BitoTiposBase.t52.cifra(), color = colores.tinta)
            CifraRodillo(minutos % 60, estilo = BitoTiposBase.t52.cifra(), color = colores.tinta, formato = { "%02d".format(it) })
        }
    }
}
