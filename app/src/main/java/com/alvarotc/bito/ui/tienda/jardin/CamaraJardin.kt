package com.alvarotc.bito.ui.tienda.jardin

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.garden.SceneRect
import com.alvarotc.bito.ui.sistema.Muelles
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Dónde mira la cámara: escala y traslación (en píxeles de la vista), con el origen arriba a la izquierda de la escena. */
data class Encuadre(
    val escala: Float,
    val tx: Float,
    val ty: Float,
)

/** La cámara de la tienda (`camObj` de tienda-jardin.js y tienda-jardin.md «Cámara (3.2)»). */
internal object MedidasCamara {
    // Se acerca a 1,12 si el objeto y su sombra caben enteros; si no, se queda en 1.
    const val ESCALA_CERCA = 1.12f

    // A los lados y por abajo, y lo que queda libre bajo el título.
    val MARGEN = 8.dp
    val LIBRE_ARRIBA = 66.dp

    // Adónde se quiere llevar el pie del objeto: al centro a lo ancho y al 66 % del alto de la vista.
    const val ANCLA_X = 0.5f
    const val ANCLA_Y = 0.66f

    // La caja que ha de caber: el objeto con su sombra (110 % del ancho y 18 % de alto centrada en el pie), con holgura.
    const val SOMBRA_LADO = 0.6f
    const val SOMBRA_ABAJO = 0.1f
}

/** La caja de un objeto con su sombra, la que la cámara deja entera: un 60 % del ancho a cada lado del centro y un 10 % bajo el pie. */
internal fun cajaConSombra(r: SceneRect): SceneRect {
    val centro = r.left + r.width / 2f
    val lado = r.width * MedidasCamara.SOMBRA_LADO
    return SceneRect(centro - lado, r.top, lado * 2f, r.height + r.width * MedidasCamara.SOMBRA_ABAJO)
}

/** El jardín entero: escala 1 y la escena (más alta que la vista) apoyada en el borde de abajo. */
internal fun encuadreEntero(
    escenaW: Float,
    escenaH: Float,
    vistaW: Float,
    vistaH: Float,
): Encuadre =
    Encuadre(
        escala = 1f,
        tx = ((vistaW - escenaW) / 2f).coerceIn(minOf(0f, vistaW - escenaW), 0f),
        ty = minOf(0f, vistaH - escenaH),
    )

/**
 * Adónde va la cámara para enseñar [caja] (ya con su sombra, en píxeles de la escena): prueba [MedidasCamara.ESCALA_CERCA]
 * con la caja a [margen] de los lados y de abajo y [libreArriba] libre arriba; si no cabe, escala 1. La traslación
 * busca el pie en el ancla y nunca enseña lo que hay fuera de la escena.
 */
internal fun encuadreDe(
    caja: SceneRect,
    escenaW: Float,
    escenaH: Float,
    vistaW: Float,
    vistaH: Float,
    margen: Float,
    libreArriba: Float,
): Encuadre {
    val pieX = caja.left + caja.width / 2f
    val pieY = caja.top + caja.height - caja.width / (2f * MedidasCamara.SOMBRA_LADO) * MedidasCamara.SOMBRA_ABAJO
    for (s in listOf(MedidasCamara.ESCALA_CERCA, 1f)) {
        // Los topes de la escena: nunca se ve lo de fuera.
        val txMin = vistaW - escenaW * s
        val tyMin = vistaH - escenaH * s
        // Lo que pide la caja: a [margen] de cada lado y de abajo, y [libreArriba] arriba.
        val xLo = maxOf(txMin, margen - caja.left * s)
        val xHi = minOf(0f, vistaW - margen - (caja.left + caja.width) * s)
        val yLo = maxOf(tyMin, libreArriba - caja.top * s)
        val yHi = minOf(0f, vistaH - margen - (caja.top + caja.height) * s)
        val cabe = xLo <= xHi && yLo <= yHi
        if (cabe || s == 1f) {
            val x = (vistaW * MedidasCamara.ANCLA_X - pieX * s).coerceIn(minOf(xLo, xHi), maxOf(xLo, xHi))
            val y = (vistaH * MedidasCamara.ANCLA_Y - pieY * s).coerceIn(minOf(yLo, yHi), maxOf(yLo, yHi))
            return Encuadre(s, x.coerceIn(minOf(txMin, 0f), 0f), y.coerceIn(minOf(tyMin, 0f), 0f))
        }
    }
    return encuadreEntero(escenaW, escenaH, vistaW, vistaH)
}

/**
 * La cámara del jardín de la tienda: un `Animatable` por eje (escala, tx, ty) con `Muelles.camara` (1,0 / 260, sin
 * rebote). Ir a otro sitio a mitad parte de donde está y con la velocidad que lleva. Se lee solo en `graphicsLayer`.
 */
@Stable
class CamaraJardin {
    private val escala = Animatable(1f)
    private val tx = Animatable(0f)
    private val ty = Animatable(0f)

    val actual: Encuadre get() = Encuadre(escala.value, tx.value, ty.value)

    /** Va a [destino] con el muelle `camara`; en modo reducido, de golpe (como el mock). */
    suspend fun ir(
        destino: Encuadre,
        reducido: Boolean = false,
    ) {
        if (reducido) return fijar(destino)
        coroutineScope {
            launch { escala.animateTo(destino.escala, Muelles.camara(umbral = UMBRAL_ESCALA)) }
            launch { tx.animateTo(destino.tx, Muelles.camara(umbral = UMBRAL_PX)) }
            launch { ty.animateTo(destino.ty, Muelles.camara(umbral = UMBRAL_PX)) }
        }
    }

    /** Se pone en [destino] sin animar. */
    suspend fun fijar(destino: Encuadre) {
        escala.snapTo(destino.escala)
        tx.snapTo(destino.tx)
        ty.snapTo(destino.ty)
    }

    private companion object {
        const val UMBRAL_ESCALA = 0.0005f
        const val UMBRAL_PX = 0.1f
    }
}

/** Aplica la [camara] a la escena en la fase de dibujo: ninguna recomposición por fotograma. */
fun Modifier.camaraJardin(camara: CamaraJardin): Modifier =
    graphicsLayer {
        val e = camara.actual
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = e.escala
        scaleY = e.escala
        translationX = e.tx
        translationY = e.ty
    }
