package com.alvarotc.bito.ui.tienda

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Muelles
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * La luz del armario (`LUZ_A`, `LUZ_B` y `stopAt` de `tienda-habi.js`). La luz va de 0 a 1 (Día, Tarde, Noche);
 * el mock la lleva de 0 a 2, así que aquí se duplica antes de interpolar.
 */
object LuzArmario {
    private val ARRIBA = listOf(Color(0xFFFFFFFF), Color(0xFFFFCF9A), Color(0xFF31406E))
    private val ABAJO = listOf(Color(0xFFFFFFFF), Color(0xFFFFDDB8), Color(0xFF4B5A8A))

    /** La luna: el astro en el que acaba de convertirse el oro. */
    val LUNA = Color(0xFFE4E0F2)

    /** Cuánto de la velocidad se proyecta al soltar el astro. */
    const val PROYECCION_S = 0.12f

    private const val LUNA_DESDE = 1.2f
    private const val LUNA_HASTA = 1.9f

    /** El color de arriba del degradado que se multiplica sobre el armario. */
    fun arriba(luz: Float): Color = entre(ARRIBA, luz * 2f)

    /** El color de abajo del degradado. */
    fun abajo(luz: Float): Color = entre(ABAJO, luz * 2f)

    /** El astro: oro hasta la tarde y luna de noche (`smooth(1.2, 1.9, v)`). */
    fun astro(
        luz: Float,
        oro: Color,
    ): Color = mezcla(oro, LUNA, suavizado(LUNA_DESDE, LUNA_HASTA, luz * 2f))

    /**
     * Dónde se queda el astro al soltarlo: a la parada más cercana tras proyectar [PROYECCION_S] de [velocidad]
     * (luz por segundo). El modo reducido no proyecta nada.
     */
    fun alSoltar(
        luz: Float,
        velocidad: Float,
        reducido: Boolean = false,
    ): LuzTienda = masCercana(luz + if (reducido) 0f else velocidad * PROYECCION_S)

    /** La parada más cercana a [luz] (0 a 1). */
    fun masCercana(luz: Float): LuzTienda {
        val i = redondear(luz * 2f).coerceIn(0, LuzTienda.entries.lastIndex)
        return LuzTienda.entries[i]
    }

    private fun entre(
        paradas: List<Color>,
        v: Float,
    ): Color {
        val x = v.coerceIn(0f, 2f)
        val i = minOf(1, floor(x).toInt())
        return mezcla(paradas[i], paradas[i + 1], x - i)
    }

    private fun suavizado(
        a: Float,
        b: Float,
        x: Float,
    ): Float {
        val q = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return q * q * (3f - 2f * q)
    }

    private fun redondear(x: Float): Int = floor(x + 0.5f).toInt()

    /** `mixHex` del mock: canal a canal en sRGB, con el redondeo de `Math.round` (el .5 sube). */
    private fun mezcla(
        a: Color,
        b: Color,
        f: Float,
    ): Color {
        fun canal(
            x: Float,
            y: Float,
        ): Float {
            val c0 = (x * 255f).roundToInt()
            val c1 = (y * 255f).roundToInt()
            return redondear(c0 + (c1 - c0) * f).coerceIn(0, 255) / 255f
        }
        return Color(canal(a.red, b.red), canal(a.green, b.green), canal(a.blue, b.blue))
    }
}

/**
 * El valor vivo de la luz (0 a 1) con su muelle. El panel lo lee en la fase de dibujo (`luz = { estado.valor }`);
 * el selector lo lleva con el dedo o a una parada.
 */
@Stable
class LuzEstado(inicial: LuzTienda = LuzTienda.DIA) {
    private val animacion = Animatable(inicial.parada)

    /** La última parada a la que se ha mandado el astro; cambia en el acto, sin esperar al muelle. */
    var destino: LuzTienda by mutableStateOf(inicial)
        internal set

    val valor: Float get() = animacion.value

    /** Lleva el astro a [parada] con `Muelles.luz` (en modo reducido, un fundido de 150 ms). Interrumpible. */
    suspend fun ir(
        parada: LuzTienda,
        reducido: Boolean = false,
    ) {
        destino = parada
        val espec =
            if (reducido) {
                tween<Float>(Duraciones.REDUCIDO, easing = Curvas.out)
            } else {
                Muelles.luz<Float>(umbral = UMBRAL)
            }
        animacion.animateTo(parada.parada, espec)
    }

    /** Sigue al dedo sin muelle: el valor es el que se pide, al instante. */
    suspend fun seguir(luz: Float) {
        animacion.snapTo(luz.coerceIn(0f, 1f))
    }

    private companion object {
        const val UMBRAL = 0.001f
    }
}

@Composable
fun rememberLuzEstado(inicial: LuzTienda = LuzTienda.DIA): LuzEstado = remember { LuzEstado(inicial) }
