package com.alvarotc.bito.ui.tienda.jardin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.garden.PoseObjeto
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Gesto
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** La caída del objeto que se prueba (`J.soltar` de tienda-jardin.js) y el meceo de lo que ya es tuyo (`J.mecer`). Sin token. */
internal object MedidasCaida {
    // Cae 150 dp en 220 ms con `t²`; empieza al 20 % de opacidad y llega entero a los 4/7 de la caída.
    val CAIDA = 150.dp
    const val CAIDA_MS = 220
    const val OPACIDAD_INICIAL = 0.2f
    const val OPACIDAD_SUBE = 1.4f

    // Al tocar tierra se aplasta un 6 % desde la base y ensancha un 60 % de eso; vuelve con `Muelles.caida`.
    const val APLASTE = 0.06f
    const val ENSANCHA = 0.6f

    // Saltan 9 granos de tierra (como mucho 10 partículas a la vez), uno cada 10 ms, de 420 ms cada uno (`B.polvo`).
    const val GRANOS = 9
    const val MAX_PARTICULAS = 10
    const val GRANO_MS = 420
    const val GRANO_ESCALON_MS = 10
    const val GRANO_ABRE = 2.4f
    val GRANO_VEL_MIN = 14.dp
    val GRANO_VEL_EXTRA = 14.dp
    val GRANO_LADO = 10.dp
    const val GRANO_ESTIRA_X = 1.5f
    const val GRANO_ESTIRA_Y = 1.6f
    val GRANO_GRAVEDAD = 70.dp
    const val GRANO_APAGA_DESDE = 0.6f
    val GRANO_ANCHO = 6.dp
    val GRANO_ALTO = 5.dp

    // El meceo de un objeto: `B.mecer(w, 60)`, un impulso de 60 grados por segundo con `Muelles.planta`.
    const val IMPULSO_MECER = 60f

    // Reducido, en vez de mecerse brilla 300 ms (como las plantas).
    const val BRILLO_MS = 300
}

/** Un grano de tierra: su salida (radianes), su velocidad y su desvío a un lado, en píxeles. */
private class Grano(val angulo: Float, val velocidad: Float, val lado: Float, val retrasoMs: Int)

/**
 * Lo que se mueve del objeto a prueba y del que ya es tuyo, en `Animatable` que solo se leen al dibujar ([pose] y
 * [pintarGranos]): la escena no se recompone por fotograma. Una llamada nueva interrumpe la anterior.
 */
@Stable
class CaidaObjeto {
    private var cayendo by mutableStateOf<String?>(null)
    private var meciendo by mutableStateOf<String?>(null)
    private var granos by mutableStateOf<List<Grano>>(emptyList())
    private var origen = Offset.Zero
    private var meceos = 0

    // El avance de la caída ya con su curva: 0 arriba, 1 en tierra.
    private val caida = Animatable(1f)
    private val aplaste = Animatable(0f)
    private val fundido = Animatable(1f)
    private val giro = Animatable(0f)
    private val brillo = Animatable(0f)
    private val reloj = Animatable(1f)

    /** Píxeles por dp: la caída y los granos se miden en dp. La pone la escena. */
    internal var densidad = 1f

    /** Lo que se pinta este fotograma de [clave], o null si no se mueve. Se llama al dibujar. */
    fun pose(clave: String): PoseObjeto? {
        val cae = clave == cayendo
        val mece = clave == meciendo
        if (!cae && !mece) return null
        if (!cae) return PoseObjeto(giro = giro.value, brillo = brillo.value)
        val p = caida.value
        val a = aplaste.value
        return PoseObjeto(
            dy = -MedidasCaida.CAIDA.value * densidad * (1f - p),
            escalaX = 1f + a * MedidasCaida.ENSANCHA,
            escalaY = 1f - a,
            alpha = fundido.value * minOf(1f, MedidasCaida.OPACIDAD_INICIAL + p * MedidasCaida.OPACIDAD_SUBE),
            giro = if (mece) giro.value else 0f,
            brillo = if (mece) brillo.value else 0f,
            sombra = p * fundido.value,
        )
    }

    /** Cuántos granos se ven ahora. */
    internal fun granosVisibles(): Int =
        granos.count {
            val p = progresoDe(it)
            p > 0f && p < 1f
        }

    /** Cuántos granos ha soltado la última caída. */
    internal val granosLanzados: Int get() = granos.size

    /**
     * [clave] cae en su sitio con su base en [base] (píxeles de la escena): `POSAR` y `LOW` al tocar tierra, se aplasta
     * y suelta los granos. Reducido: sin caída, ni granos, ni aplastar; un fundido de 150 ms (el sonido no cambia).
     */
    suspend fun caer(
        clave: String,
        base: Offset,
        reducido: Boolean,
        gesto: Gesto,
    ) {
        cayendo = clave
        if (meciendo == clave) meciendo = null
        origen = base
        granos = emptyList()
        aplaste.snapTo(0f)
        if (reducido) {
            caida.snapTo(1f)
            fundido.snapTo(0f)
            gesto(Snd.POSAR, Haptica.LOW)
            fundido.animateTo(1f, tween(Duraciones.REDUCIDO, easing = Curvas.out))
            return
        }
        fundido.snapTo(1f)
        caida.snapTo(0f)
        caida.animateTo(1f, tween(MedidasCaida.CAIDA_MS, easing = Curvas.cae))
        gesto(Snd.POSAR, Haptica.LOW)
        granos = sembrar(clave)
        coroutineScope {
            launch {
                reloj.snapTo(0f)
                reloj.animateTo(1f, tween(DURACION_GRANOS, easing = Curvas.lin))
            }
            aplaste.snapTo(MedidasCaida.APLASTE)
            aplaste.animateTo(0f, Muelles.caida(umbral = UMBRAL))
        }
    }

    /** [clave] se mece una vez desde el pie con `Muelles.planta`, con `PLANTA` y `TICK`; reducido, brilla. */
    suspend fun mecer(
        clave: String,
        reducido: Boolean,
        gesto: Gesto,
    ) {
        val mio = ++meceos
        meciendo = clave
        gesto(Snd.PLANTA)
        try {
            if (reducido) {
                brillo.snapTo(1f)
                brillo.animateTo(0f, tween(MedidasCaida.BRILLO_MS))
            } else {
                giro.snapTo(0f)
                giro.animateTo(0f, Muelles.planta(), initialVelocity = MedidasCaida.IMPULSO_MECER)
            }
        } finally {
            // Un meceo cortado (cambio de pestaña) nunca deja el objeto torcido; uno más nuevo no se toca.
            if (meceos == mio && meciendo == clave) meciendo = null
        }
    }

    /** La escena se va: nada queda cayendo, mecido ni con granos. */
    fun parar() {
        cayendo = null
        meciendo = null
        granos = emptyList()
    }

    /** La prueba se va: el objeto desaparece sin animación. */
    fun quitar() {
        cayendo = null
        granos = emptyList()
    }

    /** Los granos que saltan, en píxeles de la escena. Se llama al dibujar. */
    internal fun DrawScope.pintarGranos(color: Color) {
        val lista = granos
        if (lista.isEmpty()) return
        val d = densidad
        val ancho = MedidasCaida.GRANO_ANCHO.value * d
        val alto = MedidasCaida.GRANO_ALTO.value * d
        for (g in lista) {
            val p = progresoDe(g)
            if (p <= 0f || p >= 1f) continue
            val x = origen.x + g.lado + cos(g.angulo) * g.velocidad * p * MedidasCaida.GRANO_ESTIRA_X
            val y = origen.y + sin(g.angulo) * g.velocidad * p * MedidasCaida.GRANO_ESTIRA_Y + MedidasCaida.GRANO_GRAVEDAD.value * d * p * p
            val desde = MedidasCaida.GRANO_APAGA_DESDE
            val alfa = if (p < desde) 1f else 1f - (p - desde) / (1f - desde)
            drawOval(color.copy(alpha = alfa.coerceIn(0f, 1f)), Offset(x - ancho / 2f, y - alto / 2f), Size(ancho, alto))
        }
    }

    private fun progresoDe(g: Grano): Float = (reloj.value * DURACION_GRANOS - g.retrasoMs) / MedidasCaida.GRANO_MS

    private fun sembrar(clave: String): List<Grano> {
        val azar = Random(clave.hashCode())
        val d = densidad
        val n = minOf(MedidasCaida.GRANOS, MedidasCaida.MAX_PARTICULAS)
        return List(n) { i ->
            Grano(
                angulo = (-Math.PI / 2).toFloat() + (azar.nextFloat() - 0.5f) * MedidasCaida.GRANO_ABRE,
                velocidad = (MedidasCaida.GRANO_VEL_MIN.value + azar.nextFloat() * MedidasCaida.GRANO_VEL_EXTRA.value) * d,
                lado = (azar.nextFloat() - 0.5f) * MedidasCaida.GRANO_LADO.value * d,
                retrasoMs = i * MedidasCaida.GRANO_ESCALON_MS,
            )
        }
    }

    private companion object {
        val DURACION_GRANOS = MedidasCaida.GRANO_MS + (MedidasCaida.GRANOS - 1) * MedidasCaida.GRANO_ESCALON_MS
        const val UMBRAL = 0.0005f
    }
}
