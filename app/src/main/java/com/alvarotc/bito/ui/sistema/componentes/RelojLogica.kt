package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.ui.graphics.Color
import com.alvarotc.bito.ui.sistema.Escena
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

/** La geometría del reloj de cielo (G29): 0 min arriba, sentido horario, 24 h por vuelta. */
object RelojLogica {
    const val DIA = 24 * 60

    /** Ángulo en grados (0 arriba, horario) del punto (dx, dy) respecto al centro, con y hacia abajo. */
    fun anguloDePunto(
        dx: Float,
        dy: Float,
    ): Float = ((Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()) + 360f) % 360f

    fun minutoDeAngulo(
        grados: Float,
        paso: Int,
    ): Int {
        val g = ((grados % 360f) + 360f) % 360f
        return ((g / 360f * DIA / paso).roundToInt() * paso) % DIA
    }

    fun anguloDeMinuto(minuto: Int): Float = minuto.mod(DIA) / DIA.toFloat() * 360f

    fun distanciaCircular(
        a: Int,
        b: Int,
    ): Int {
        val d = abs(a - b) % DIA
        return min(d, DIA - d)
    }

    /** Dentro de [desde]–[hasta] se queda; fuera, el extremo más cercano por el círculo. */
    fun acotar(
        minuto: Int,
        desde: Int,
        hasta: Int,
    ): Int =
        when {
            minuto in desde..hasta -> minuto
            distanciaCircular(minuto, desde) <= distanciaCircular(minuto, hasta) -> desde
            else -> hasta
        }

    /** Delta con signo por el camino corto (−720..720). */
    fun caminoCorto(
        desde: Int,
        hasta: Int,
    ): Int {
        var d = (hasta - desde) % DIA
        if (d > DIA / 2) d -= DIA
        if (d < -DIA / 2) d += DIA
        return d
    }

    fun colorCielo(minuto: Int): Color {
        val m = minuto.mod(DIA)
        val paradas = Escena.relojCielo
        val i = paradas.indexOfLast { it.first <= m }.coerceIn(0, paradas.size - 2)
        val (m0, c0) = paradas[i]
        val (m1, c1) = paradas[i + 1]
        val t = if (m1 == m0) 0f else (m - m0).toFloat() / (m1 - m0)
        return mezclar(c0, c1, t)
    }

    /** Mezcla lineal canal a canal en sRGB, como las paradas de `CIELO_RELOJ` de sistema.js. */
    private fun mezclar(
        a: Color,
        b: Color,
        t: Float,
    ): Color = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)

    /** 0 = luna, 1 = sol. Mezcla de 6:00 a 7:30 y de 19:00 a 20:30. */
    fun mezclaSol(minuto: Int): Float {
        val m = minuto.mod(DIA)
        return when {
            m < 360 -> 0f
            m < 450 -> (m - 360) / 90f
            m < 1140 -> 1f
            m < 1230 -> 1f - (m - 1140) / 90f
            else -> 0f
        }
    }
}
