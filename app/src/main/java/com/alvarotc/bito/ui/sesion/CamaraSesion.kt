package com.alvarotc.bito.ui.sesion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Gesto
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Los encuadres de la sesión (`ENC` del mock). [s] es la escala de la escena del mock (1225 × 915); [pie] es la
 * y de los pies de Habi y [suelo] la de la tierra, en dp desde arriba de una pantalla de 915 dp.
 */
enum class Encuadre(val s: Float, val pie: Int, val suelo: Int) {
    HOY(0.40f, 322, 366),
    PREPARAR(0.60f, 370, 398),
    RESPIRAR(1.00f, 640, 700),
    FOCO(0.74f, 440, 480),
    DESCANSO(0.60f, 330, 360),
    TERMINADA(0.72f, 440, 500),
    SUELTA_ELEGIR(0.74f, 430, 480),
    SUELTA_MARCHA(1.00f, 640, 700),
    COMO_ESTAS(0.74f, 410, 450),
    SUELTA_FIN(0.80f, 470, 540),
}

// El alto de pantalla que pinta el mock: `pie` y `suelo` se escalan por altoPantalla / 915 (H1).
private const val ALTO_MOCK = 915f

// Los pies de Habi, en la fracción del alto de la escena (`HB` del mock).
private const val PIES_HABI = 0.88f

// Lo que la escena baja por debajo del suelo como mínimo (`suelo + 8` en `pintaCam()`).
private val HOLGURA_SUELO = 8.dp

// Lo que cambia la escala para que la cámara suene a acercarse o alejarse (`ir()`).
private const val UMBRAL_SONIDO = 0.1f

// Opacidad desde la que se funde el salto del modo reducido (`encuadre()`).
private const val ALFA_REDUCIDO = 0.4f

// Umbrales de los muelles: la escala se mueve en milésimas y pie y suelo en dp.
private const val UMBRAL_ESCALA = 0.0005f
private const val UMBRAL_DP = 0.1f

/** La transformación de la escena: escala desde su esquina de arriba a la izquierda y traslación, en px. */
internal data class Toma(val escala: Float, val tx: Float, val ty: Float)

/**
 * El suelo en px de la pantalla: el del encuadre escalado por [altoPantalla] / 915 y, si el bloque de la tierra
 * ([altoContenido], px) no cabe debajo, subido lo justo (H1).
 */
internal fun sueloEnPantalla(
    suelo: Float,
    altoPantalla: Float,
    altoContenido: Float,
): Float = min(suelo * altoPantalla / ALTO_MOCK, altoPantalla - altoContenido).coerceAtLeast(0f)

/**
 * `pintaCam()` del mock sobre el jardín del Inicio, que mide [escenaW] × [escenaH] px a lo ancho de la pantalla.
 * [s] se aplica relativo a Hoy (factor s / 0,40 sobre la escala con la que Hoy cubre el 40 % del alto), con los
 * pies de Habi en [pie] y centrado en horizontal; la traslación se limita para que nunca asome el borde:
 * `tx ∈ [ancho − escala·W, 0]`, `ty ∈ [suelo + 8 − escala·H, 0]`. [pie] y [suelo] van en dp del mock.
 */
internal fun tomaDeCamara(
    s: Float,
    pie: Float,
    suelo: Float,
    altoPantalla: Float,
    escenaW: Float,
    escenaH: Float,
    holgura: Float,
    altoContenido: Float = 0f,
): Toma {
    val cubrirHoy = max(1f, Encuadre.HOY.s * altoPantalla / escenaH)
    val escala = cubrirHoy * s / Encuadre.HOY.s
    val piePx = pie * altoPantalla / ALTO_MOCK
    val sueloPx = sueloEnPantalla(suelo, altoPantalla, altoContenido)
    val tx = min(0f, max(escenaW - escala * escenaW, escenaW / 2f - escala * escenaW / 2f))
    val ty = min(0f, max(sueloPx + holgura - escala * escenaH, piePx - escala * PIES_HABI * escenaH))
    return Toma(escala, tx, ty)
}

/**
 * La cámara de la sesión: tres muelles `camara` (escala, pie de Habi y suelo) que se pueden interrumpir a mitad,
 * y la opacidad del salto reducido. Todo se lee al dibujar ([camaraSesion] y la tierra con [sueloEn] y [alfa]):
 * moverla no recompone.
 */
@Stable
class EstadoCamaraSesion internal constructor(inicial: Encuadre) {
    var destino: Encuadre by mutableStateOf(inicial)
        private set

    internal var reducido: Boolean = false

    private val escalaAnim = Animatable(inicial.s, UMBRAL_ESCALA)
    private val pieAnim = Animatable(inicial.pie.toFloat(), UMBRAL_DP)
    private val sueloAnim = Animatable(inicial.suelo.toFloat(), UMBRAL_DP)
    private val alfaAnim = Animatable(1f)

    internal val escala: Float get() = escalaAnim.value

    /** El pie de Habi, en dp del mock (pantalla de 915). */
    internal val pie: Float get() = pieAnim.value

    /** El suelo, en dp del mock (pantalla de 915). */
    internal val suelo: Float get() = sueloAnim.value

    /** La opacidad de la escena y de la tierra: 1 salvo en el fundido del salto reducido. */
    val alfa: Float get() = alfaAnim.value

    /** La y del suelo en px para una pantalla de [altoPantalla] px con un bloque de [altoContenido] px (H1). */
    fun sueloEn(
        altoPantalla: Float,
        altoContenido: Float,
    ): Float = sueloEnPantalla(sueloAnim.value, altoPantalla, altoContenido)

    internal fun toma(
        altoPantalla: Float,
        escenaW: Float,
        escenaH: Float,
        holgura: Float,
        altoContenido: Float,
    ): Toma = tomaDeCamara(escalaAnim.value, pieAnim.value, sueloAnim.value, altoPantalla, escenaW, escenaH, holgura, altoContenido)

    /**
     * Lleva la cámara a [a]. El sonido y la háptica (`ir()` del mock) salen con [gesto] en el mismo fotograma en que
     * empieza a moverse. Reducido: salta y la escena se funde de 0,4 a 1 en 150 ms.
     */
    suspend fun ir(
        a: Encuadre,
        gesto: Gesto?,
    ) {
        val antes = destino
        destino = a
        if (antes != a) gesto?.let { sonar(antes, a, it) }
        coroutineScope {
            if (reducido) {
                escalaAnim.snapTo(a.s)
                pieAnim.snapTo(a.pie.toFloat())
                sueloAnim.snapTo(a.suelo.toFloat())
                alfaAnim.snapTo(ALFA_REDUCIDO)
                alfaAnim.animateTo(1f, tween(Duraciones.REDUCIDO, easing = Curvas.lin))
            } else {
                if (alfaAnim.value != 1f) launch { alfaAnim.snapTo(1f) }
                launch { escalaAnim.animateTo(a.s, Muelles.camara(umbral = UMBRAL_ESCALA)) }
                launch { pieAnim.animateTo(a.pie.toFloat(), Muelles.camara(umbral = UMBRAL_DP)) }
                launch { sueloAnim.animateTo(a.suelo.toFloat(), Muelles.camara(umbral = UMBRAL_DP)) }
            }
        }
    }

    private fun sonar(
        antes: Encuadre,
        a: Encuadre,
        gesto: Gesto,
    ) {
        when {
            antes == Encuadre.HOY -> gesto(Snd.ZOOM, Haptica.RISE)
            a == Encuadre.HOY -> gesto(Snd.ASCENSO, Haptica.LOW)
            a.s > antes.s + UMBRAL_SONIDO -> gesto(Snd.ACERCAR, Haptica.RISE)
            a.s < antes.s - UMBRAL_SONIDO -> gesto(Snd.ALEJAR, Haptica.TICK)
        }
    }
}

@Composable
fun rememberEstadoCamaraSesion(inicial: Encuadre = Encuadre.HOY): EstadoCamaraSesion {
    val estado = remember { EstadoCamaraSesion(inicial) }
    val reducido = BitoTema.reducido
    SideEffect { estado.reducido = reducido }
    return estado
}

/**
 * La cámara sobre el jardín del Inicio. El elemento ha de estar colocado arriba a la izquierda de la zona de la
 * sesión, que mide [altoPantalla] de alto, y medir [escenaW] × [escenaH] px (el jardín a lo ancho de la pantalla).
 * [altoContenido] es el alto mínimo, en px, del bloque que pone la tierra: si no cabe, el suelo sube (H1). Solo
 * `graphicsLayer`, leído en la fase de dibujo: ninguna recomposición por fotograma.
 */
fun Modifier.camaraSesion(
    estado: EstadoCamaraSesion,
    altoPantalla: Dp,
    escenaW: Float,
    escenaH: Float,
    altoContenido: State<Int>? = null,
): Modifier =
    graphicsLayer {
        val t = estado.toma(altoPantalla.toPx(), escenaW, escenaH, HOLGURA_SUELO.toPx(), (altoContenido?.value ?: 0).toFloat())
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = t.escala
        scaleY = t.escala
        translationX = t.tx
        translationY = t.ty
        alpha = estado.alfa
    }
