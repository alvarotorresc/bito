package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Escena
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.LocalTacto
import com.alvarotc.bito.ui.sistema.sonido.LocalSonido
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

enum class FamiliaLogro { RACHAS, CONSTANCIA, MOMENTOS }

private fun FamiliaLogro.colores() =
    when (this) {
        FamiliaLogro.RACHAS -> Escena.rachas
        FamiliaLogro.CONSTANCIA -> Escena.constancia
        FamiliaLogro.MOMENTOS -> Escena.momentos
    }

private const val CLICK_MS = 600L
private const val FUNDIDO_REDUCIDO_MS = 300
private const val CHISPAS = 10

private val CURVA_ANILLO = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)
private val CURVA_DISCO = CubicBezierEasing(0.3f, 0.8f, 0.3f, 1f)
private val CURVA_CHISPAS = CubicBezierEasing(0.15f, 0.7f, 0.3f, 1f)
private val CURVA_DESTELLO = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** Avance 0–1 de un tramo que empieza en [retraso] ms y dura [duracion] ms, con la curva [curva]. */
private fun tramo(
    t: Float,
    retraso: Int,
    duracion: Int,
    curva: Easing = LinearEasing,
): Float = curva.transform(((t - retraso) / duracion).coerceIn(0f, 1f))

/**
 * Emblema de logro (dirección C, G13): anillo de [progreso] 0–1; [celebrar] lanza su momento de encenderse.
 * Lo usa la pieza 3. Sin [encendido] el molde queda apagado y el [arte] al 35 %.
 */
@Composable
fun Emblema(
    familia: FamiliaLogro,
    progreso: Float,
    encendido: Boolean,
    modifier: Modifier = Modifier,
    tamano: Dp = 120.dp,
    celebrar: Boolean = false,
    arte: @Composable () -> Unit = {},
) {
    val reducido = BitoTema.reducido
    val colores = BitoTema.colores
    val sonido = LocalSonido.current
    val tacto = LocalTacto.current
    val fam = familia.colores()
    // Milisegundos de la secuencia (0 a EMBLEMA). En reposo, encendido = secuencia terminada.
    val reloj = remember { Animatable(if (encendido) Duraciones.EMBLEMA.toFloat() else 0f) }
    val fundido = remember { Animatable(if (encendido) 1f else 0f) }
    LaunchedEffect(encendido) {
        if (!celebrar) {
            reloj.snapTo(if (encendido) Duraciones.EMBLEMA.toFloat() else 0f)
            fundido.snapTo(if (encendido) 1f else 0f)
        }
    }
    LaunchedEffect(celebrar) {
        if (!celebrar) return@LaunchedEffect
        sonido.play(Snd.LOGRO)
        if (reducido) {
            fundido.snapTo(0f)
            reloj.snapTo(0f)
            fundido.animateTo(1f, tween(FUNDIDO_REDUCIDO_MS, easing = Curvas.out))
        } else {
            fundido.snapTo(1f)
            reloj.snapTo(0f)
            reloj.animateTo(Duraciones.EMBLEMA.toFloat(), tween(Duraciones.EMBLEMA, easing = LinearEasing))
        }
    }
    LaunchedEffect(celebrar) {
        if (!celebrar) return@LaunchedEffect
        delay(CLICK_MS)
        tacto.haptica(Haptica.CLICK)
    }

    val t = reloj.value
    val celebrando = celebrar && !reducido
    // Qué tanto está encendido el disco (0 apagado, 1 encendido) y cuánto el anillo ya cerrado.
    val disco: Float
    val escalaDisco: Float
    val anillo: Float
    val silueta: Float
    if (!encendido) {
        disco = 0f
        escalaDisco = 1f
        anillo = progreso
        silueta = 1f
    } else if (celebrando && t < Duraciones.EMBLEMA) {
        val d = tramo(t, 520, 550, CURVA_DISCO)
        disco = tramo(t, 520, 300, Curvas.out)
        escalaDisco = if (d < 0.55f) 0.7f + (1.08f - 0.7f) * (d / 0.55f) else 1.08f + (1f - 1.08f) * ((d - 0.55f) / 0.45f)
        anillo = progreso + (1f - progreso) * tramo(t, 80, 500, CURVA_ANILLO)
        silueta = 1f - tramo(t, 300, 300, Curvas.out)
    } else {
        disco = fundido.value
        escalaDisco = 1f
        anillo =
            if (reducido || celebrar) {
                progreso + (1f - progreso) * fundido.value
            } else if (progreso >= 1f) {
                1f
            } else {
                progreso
            }
        silueta = 1f - fundido.value
    }
    val pulso = if (celebrando) tramo(t, 750, 700, Curvas.out) else 0f
    val chispas = if (celebrando) tramo(t, 780, 650, CURVA_CHISPAS) else 0f
    val destello = if (celebrando) tramo(t, 820, 550, CURVA_DESTELLO) else 0f

    Box(
        modifier
            .size(tamano)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progreso, 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(tamano)) {
            val s = size.minDimension / 120f
            val c = Offset(size.width / 2f, size.height / 2f)
            val rAnillo = 52f * s
            val rDisco = 40f * s
            val trazo = 6.dp.toPx() * (tamano.toPx() / 120.dp.toPx())
            val colorAnillo = if (encendido) fam.anillo else Escena.moldeBloqueado
            val colorDisco = if (encendido) fam.disco else Escena.moldeBloqueado
            // pista y anillo
            drawCircle(colores.tMedia.copy(alpha = 0.25f), rAnillo, c, style = Stroke(trazo))
            if (anillo > 0f) {
                drawArc(
                    color = colorAnillo,
                    startAngle = -90f,
                    sweepAngle = 360f * anillo,
                    useCenter = false,
                    topLeft = Offset(c.x - rAnillo, c.y - rAnillo),
                    size = Size(rAnillo * 2, rAnillo * 2),
                    style = Stroke(trazo, cap = StrokeCap.Round),
                )
            }
            // molde (silueta) bajo el disco
            if (silueta > 0f) drawCircle(Escena.moldeBloqueado.copy(alpha = silueta), rDisco, c)
            // pulso
            if (pulso > 0f && pulso < 1f) {
                drawCircle(
                    colores.aguaHonda.copy(alpha = 0.9f * (1f - pulso)),
                    rDisco * (1f + 0.5f * pulso),
                    c,
                    style = Stroke(2.4f * s),
                )
            }
            // disco encendido
            if (disco > 0f) {
                val e = escalaDisco
                drawCircle(colorDisco.copy(alpha = disco), rDisco * e, c)
                drawCircle(colores.contorno.copy(alpha = disco), rDisco * e, c, style = Stroke(1.5f * s))
            }
            if (destello > 0f && destello < 1f) destelloEn(c, rDisco, s, destello)
            if (chispas > 0f && chispas < 1f) chispasEn(c, s, chispas, colores.aguaHonda)
        }
        // El arte: al 35 % mientras es silueta, entero cuando el disco se enciende.
        val alfaArte = 0.35f + 0.65f * (if (encendido) disco else 0f)
        val escalaArte = if (encendido) escalaDisco else 1f
        Box(
            Modifier
                .size(tamano * (64f / 120f))
                .graphicsLayer {
                    alpha = alfaArte
                    scaleX = escalaArte
                    scaleY = escalaArte
                },
            contentAlignment = Alignment.Center,
        ) { arte() }
    }
}

private fun DrawScope.destelloEn(
    c: Offset,
    rDisco: Float,
    s: Float,
    avance: Float,
) {
    val alfa =
        when {
            avance < 0.15f -> avance / 0.15f
            avance > 0.85f -> (1f - avance) / 0.15f
            else -> 1f
        }
    val alto = 120f * s
    val ancho = 13f * s
    val desvio = tan(22.0 * PI / 180.0).toFloat() * alto
    val x = c.x - 60f * s + (20f + 140f * avance) * s
    val cinta =
        Path().apply {
            moveTo(x, c.y - alto / 2)
            lineTo(x + ancho, c.y - alto / 2)
            lineTo(x + ancho - desvio, c.y + alto / 2)
            lineTo(x - desvio, c.y + alto / 2)
            close()
        }
    val circulo = Path().apply { addOval(Rect(c, rDisco)) }
    clipPath(circulo) { drawPath(cinta, Color.White.copy(alpha = 0.45f * alfa)) }
}

private fun DrawScope.chispasEn(
    c: Offset,
    s: Float,
    avance: Float,
    color: Color,
) {
    val alfa = if (avance < 0.15f) avance / 0.15f else 1f - (avance - 0.15f) / 0.85f
    repeat(CHISPAS) { i ->
        val a = i / CHISPAS.toFloat() * 2f * PI.toFloat() - PI.toFloat() / 2f + if (i % 2 == 1) 0.14f else 0f
        val d = (14f + (i % 3) * 4f) * s * avance
        val ux = cos(a)
        val uy = sin(a)
        drawLine(
            color.copy(alpha = alfa.coerceIn(0f, 1f)),
            Offset(c.x + 56f * s * ux + d * ux, c.y + 56f * s * uy + d * uy),
            Offset(c.x + 63f * s * ux + d * ux, c.y + 63f * s * uy + d * uy),
            strokeWidth = 2.6f * s,
            cap = StrokeCap.Round,
        )
    }
}
