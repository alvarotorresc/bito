package com.alvarotc.bito.ui.tienda

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.CifraRodillo
import com.alvarotc.bito.ui.sistema.componentes.Monedita

/** Un destino del vuelo: la posición (en coordenadas de la raíz) donde aterriza la moneda. Se lee al salir. */
fun interface DestinoMoneda {
    fun posicion(): Offset
}

/** La geometría del vuelo (`monedaEn` del mock): una curva cuadrática que se abre a la derecha y baja. */
object TrayectoMoneda {
    /** Cuánto se abre a la derecha el punto de control, en dp (`cx = x1 + 150`). */
    const val ABRE_DP = 150f
    private const val ALTURA_CONTROL = 0.3f
    private const val ESCALA_FINAL = 0.6f
    private const val INICIO_FUNDIDO = 0.85f

    fun posicion(
        p: Float,
        origen: Offset,
        destino: Offset,
        abre: Float,
    ): Offset {
        val q = 1f - p
        val cx = destino.x + abre
        val cy = origen.y + (destino.y - origen.y) * ALTURA_CONTROL
        return Offset(
            q * q * origen.x + 2f * q * p * cx + p * p * destino.x,
            q * q * origen.y + 2f * q * p * cy + p * p * destino.y,
        )
    }

    fun escala(p: Float): Float = 1f - (1f - ESCALA_FINAL) * p

    fun opacidad(p: Float): Float = if (p > INICIO_FUNDIDO) (1f - p) / (1f - INICIO_FUNDIDO) else 1f
}

/** Una moneda en el aire. Lo que cambia por fotograma es `progreso`, que solo se lee en la fase de dibujo. */
class MonedaEnVuelo internal constructor(
    internal val origen: Offset,
    internal val destino: Offset,
    internal val reducido: Boolean,
) {
    internal val avance = Animatable(0f)
    val progreso: Float get() = avance.value

    fun posicion(abre: Float): Offset = if (reducido) destino else TrayectoMoneda.posicion(progreso, origen, destino, abre)

    val escala: Float get() = if (reducido) 1f else TrayectoMoneda.escala(progreso)

    /** Reducido: se funde en el destino, sin arco. */
    val opacidad: Float get() = if (reducido) progreso else TrayectoMoneda.opacidad(progreso)
}

/**
 * Lanza monedas del saldo a un destino. Registra el origen con [origenMoneda] y pinta con [CapaMoneda].
 * Una moneda cuyo vuelo se cancela desaparece con él: nunca queda una a medias.
 */
class VueloMoneda {
    private var origen by mutableStateOf<Offset?>(null)
    private val vuelos = mutableStateListOf<MonedaEnVuelo>()

    internal var reducido = false

    val activas: List<MonedaEnVuelo> get() = vuelos

    fun fijarOrigen(posicion: Offset?) {
        origen = posicion
    }

    /** Vuela hasta que la moneda llega. Sin origen no vuela y vuelve enseguida; cancelado, la quita. */
    suspend fun volar(
        destino: DestinoMoneda,
        duracionMs: Int = Duraciones.GASTAR,
    ) {
        val salida = origen ?: return
        val moneda = MonedaEnVuelo(salida, destino.posicion(), reducido)
        vuelos.add(moneda)
        try {
            if (reducido) {
                moneda.avance.animateTo(1f, tween(Duraciones.REDUCIDO))
            } else {
                moneda.avance.animateTo(1f, tween(duracionMs, easing = Curvas.drawer))
            }
        } finally {
            vuelos.remove(moneda)
        }
    }
}

/** Marca el saldo de la cabecera como origen de las monedas: el centro del lado izquierdo, a 18 dp (`de.l + 18`). */
@Composable
fun Modifier.origenMoneda(vuelo: VueloMoneda): Modifier {
    val densidad = LocalDensity.current.density
    return onGloballyPositioned {
        val p = it.positionInRoot()
        vuelo.fijarOrigen(Offset(p.x + ORIGEN_X_DP * densidad, p.y + it.size.height / 2f))
    }
}

private const val ORIGEN_X_DP = 18f
private val MEDIA_MONEDA = 12.dp

/** La capa por encima de todo donde se dibujan las monedas. Sin recomponer por fotograma: se lee en `graphicsLayer`. */
@Composable
fun CapaMoneda(
    vuelo: VueloMoneda,
    modifier: Modifier = Modifier,
) {
    val reducido = BitoTema.reducido
    vuelo.reducido = reducido // se lee al volar, que puede llegar en el mismo fotograma
    val densidad = LocalDensity.current.density
    var raiz by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { raiz = it.positionInRoot() },
    ) {
        vuelo.activas.forEach { moneda ->
            Box(
                Modifier.graphicsLayer {
                    val pos = moneda.posicion(TrayectoMoneda.ABRE_DP * densidad) - raiz
                    val mitad = MEDIA_MONEDA.toPx()
                    translationX = pos.x - mitad
                    translationY = pos.y - mitad
                    scaleX = moneda.escala
                    scaleY = moneda.escala
                    alpha = moneda.opacidad
                },
            ) { Monedita(MEDIA_MONEDA * 2) }
        }
    }
}

/** El saldo de la cabecera: monedita y cifra que rueda en 240 ms (reducido: fundido de 150 ms). */
@Composable
fun SaldoConRodillo(
    saldo: Int,
    modifier: Modifier = Modifier,
) {
    val descripcion = pluralStringResource(R.plurals.sistema_saldo, saldo, saldo)
    Row(
        modifier
            .testTag("saldo")
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monedita(18.dp)
        Spacer(Modifier.width(Medidas.e4))
        CifraRodillo(saldo, estilo = BitoTema.tipos.t19.cifra(), color = BitoTema.colores.oroTexto)
    }
}
