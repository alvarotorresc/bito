package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.Duraciones
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Los diez granos de tierra que salta el brote (`polvo(…, 10, .8)` y `.grain` del mock). El grano `i` sale a los
 * `i · 10` ms y dura 420 ms lineales. Los números `(a, b, c)` de cada grano son fijos (el mock usa `Math.random`),
 * para que se vea igual siempre y se pueda probar. Todo en dp.
 */
internal object GranosBrote {
    const val N = 10
    private const val SALE_CADA_MS = 10
    const val VIDA_MS = Duraciones.POLVO

    /** Lo que tarda el último en apagarse: 510 ms. */
    const val TOTAL_MS = (N - 1) * SALE_CADA_MS + VIDA_MS

    val ancho = 6.dp
    val alto = 5.dp

    /** `border-radius: 45%` de `.grain`. */
    const val REDONDEO = 0.45f

    /** El tamaño de la sombra de Habi sobre 1132 unidades del lienzo, por 1,05 (`polvo` sale de la sombra). */
    private const val SOMBRA_LIENZO = 1132f * 1.05f

    /** El origen está al 40 % de la sombra: 0,032 de su ancho por encima de los pies. */
    private const val ORIGEN_SOBRE_PIES = 0.032f

    private const val ABANICO = 1.92f
    private const val ALCANCE_X = 1.5f
    private const val ALCANCE_Y = 1.6f
    private const val CAIDA = 70f
    private const val OPACO_HASTA = 0.6f

    private val trios =
        arrayOf(
            floatArrayOf(0.08f, 0.35f, 0.62f),
            floatArrayOf(0.91f, 0.12f, 0.30f),
            floatArrayOf(0.35f, 0.78f, 0.85f),
            floatArrayOf(0.62f, 0.50f, 0.10f),
            floatArrayOf(0.19f, 0.93f, 0.47f),
            floatArrayOf(0.77f, 0.27f, 0.71f),
            floatArrayOf(0.50f, 0.64f, 0.20f),
            floatArrayOf(0.03f, 0.05f, 0.93f),
            floatArrayOf(0.86f, 0.86f, 0.55f),
            floatArrayOf(0.42f, 0.20f, 0.38f),
        )

    /** Cuánto va el grano [i] de su vida (0 a 1) a los [tMs] ms de empezar el brote. */
    fun progreso(
        i: Int,
        tMs: Float,
    ): Float = ((tMs - i * SALE_CADA_MS) / VIDA_MS).coerceIn(0f, 1f)

    fun angulo(i: Int): Float = -PI.toFloat() / 2f + (trios[i][0] - 0.5f) * ABANICO

    fun velocidad(i: Int): Float = 14f + 14f * trios[i][1]

    fun lado(i: Int): Float = (trios[i][2] - 0.5f) * 10f

    fun x(
        i: Int,
        p: Float,
        x0: Float,
    ): Float = x0 + lado(i) + cos(angulo(i)) * velocidad(i) * ALCANCE_X * p

    fun y(
        i: Int,
        p: Float,
        y0: Float,
    ): Float = y0 + sin(angulo(i)) * velocidad(i) * ALCANCE_Y * p + CAIDA * p * p

    /** Opaco hasta el 60 % y luego se apaga en línea recta. */
    fun alfa(p: Float): Float = if (p <= OPACO_HASTA) 1f else ((1f - p) / (1f - OPACO_HASTA)).coerceIn(0f, 1f)

    /** El origen en y: [piesY] menos 0,032 del ancho de la sombra, que va con la escala [dpPorUnidad] del lienzo. */
    fun origenY(
        piesY: Float,
        dpPorUnidad: Float,
    ): Float = piesY - ORIGEN_SOBRE_PIES * SOMBRA_LIENZO * dpPorUnidad
}
